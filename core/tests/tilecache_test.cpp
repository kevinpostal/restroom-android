#include "restroom/core.h"

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

#include <cstdio>
#include <fstream>
#include <string>

using namespace restroom;

namespace {

Place at(long long id, double lat, double lon, const char* name = "") {
    Place p;
    p.id = id;
    p.lat = lat;
    p.lon = lon;
    p.name = name;
    return p;
}

std::string tempPath(const char* stem) {
    const char* dir = std::getenv("TMPDIR");
    std::string base = dir ? dir : "/tmp";
    if (base.back() != '/') base += '/';
    return base + "restroom-" + stem + "-" + std::to_string(static_cast<long>(::getpid())) + ".json";
}

constexpr long T0 = 1'700'000'000;

}  // namespace

TEST(TileCache, FreshServableAndMissingRing) {
    TileCache cache("");
    const Cell c{3733, -12201};
    EXPECT_FALSE(cache.isFresh(c, T0));
    EXPECT_FALSE(cache.isServable(c, T0));
    cache.store(c, {at(1, 37.33, -122.01)}, T0);
    EXPECT_TRUE(cache.isFresh(c, T0 + 599));
    EXPECT_FALSE(cache.isFresh(c, T0 + 600));
    EXPECT_TRUE(cache.isServable(c, T0 + 600));
    EXPECT_TRUE(cache.isServable(c, T0 + 86399));
    EXPECT_FALSE(cache.isServable(c, T0 + 86400));

    const auto ring = c.ring();
    EXPECT_EQ(cache.missingRing(c, T0).size(), 8u);
    cache.store(ring[0], {at(2, 37.34, -122.01)}, T0);
    cache.store(ring[3], {at(3, 37.33, -122.02)}, T0 - 700);   // stale, not fresh
    const auto missing = cache.missingRing(c, T0);
    ASSERT_EQ(missing.size(), 7u);
    EXPECT_EQ(missing[0], ring[1]);
    EXPECT_EQ(missing[1], ring[2]);
    EXPECT_EQ(missing[2], ring[3]);
    EXPECT_TRUE(cache.ringServable(ring[1], T0));    // ring[1]'s ring contains c
    EXPECT_FALSE(cache.ringServable(Cell{0, 0}, T0));
}

TEST(TileCache, PoolUnionsCentreAndRingDeduplicated) {
    TileCache cache("");
    const Cell c{3733, -12201};
    const auto ring = c.ring();
    cache.store(c, {at(1, 37.33, -122.01, "centre"), at(9, 37.331, -122.011, "shared")}, T0);
    cache.store(ring[0], {at(9, 37.331, -122.011, "shared-dup"), at(2, 37.34, -122.01, "north")}, T0);
    cache.store(ring[1], {at(3, 37.32, -122.01, "south")}, T0 - TileCache::serveTTL);   // a day old: not servable
    const auto pool = cache.pool(c, T0);
    ASSERT_EQ(pool.size(), 3u);
    EXPECT_EQ(pool[0].id, 1);
    EXPECT_EQ(pool[1].id, 9);
    EXPECT_EQ(pool[1].name, "shared");   // centre copy wins
    EXPECT_EQ(pool[2].id, 2);
}

TEST(TileCache, StoreEvictsExpiredAndEraseDrops) {
    TileCache cache("");
    const Cell a{1, 1}, b{2, 2};
    cache.store(a, {at(1, 0, 0)}, T0);
    cache.store(b, {at(2, 0, 0)}, T0 + TileCache::serveTTL);   // a is now a day old
    EXPECT_EQ(cache.size(), 1u);
    EXPECT_FALSE(cache.isServable(a, T0 + TileCache::serveTTL));
    cache.erase(b);
    EXPECT_EQ(cache.size(), 0u);
}

TEST(TileCache, SaveThenLoadRoundTripsAndDropsExpired) {
    const std::string path = tempPath("roundtrip");
    std::remove(path.c_str());
    {
        TileCache cache(path);
        Place p = at(42, 37.33, -122.01, "Happy Lemon");
        p.street = "10963 N Wolfe Road";
        p.comment = "Code is 2580";
        p.accessible = true;
        p.distanceMiles = 0.18;
        p.pin = "never-saved";
        cache.store(Cell{3733, -12201}, {p}, T0);
        Place park = at(-5, 37.33, -122.015, "Memorial Park");
        park.kind = Kind::Park;
        cache.store(Cell{3734, -12201}, {park}, T0 - 5000);
        cache.store(Cell{3735, -12201}, {at(7, 0, 0)}, T0 - 90000);   // older than serveTTL by load time
    }
    TileCache warm(path);
    warm.load(T0 + 100);
    EXPECT_TRUE(warm.isFresh(Cell{3733, -12201}, T0 + 100));
    EXPECT_TRUE(warm.isServable(Cell{3734, -12201}, T0 + 100));
    EXPECT_FALSE(warm.isServable(Cell{3735, -12201}, T0 + 100));
    const auto pool = warm.pool(Cell{3733, -12201}, T0 + 100);
    ASSERT_EQ(pool.size(), 2u);
    EXPECT_EQ(pool[0].name, "Happy Lemon");
    EXPECT_EQ(pool[0].street, "10963 N Wolfe Road");
    EXPECT_TRUE(pool[0].accessible);
    EXPECT_FALSE(pool[0].pin);   // pins are attached at publish time, never persisted
    ASSERT_TRUE(pool[0].distanceMiles);
    EXPECT_DOUBLE_EQ(*pool[0].distanceMiles, 0.18);
    EXPECT_EQ(pool[1].kind, Kind::Park);
    std::remove(path.c_str());
}

TEST(TileCache, CorruptOrMissingFileStartsCold) {
    const std::string path = tempPath("corrupt");
    { std::ofstream out(path); out << "{not json"; }
    TileCache cache(path);
    cache.load(T0);
    EXPECT_EQ(cache.size(), 0u);
    { std::ofstream out(path); out << R"({"1,2":{"at":)" << T0 << R"(,"list":[{"id":"bad"}]}})"; }
    cache.load(T0);
    EXPECT_EQ(cache.size(), 0u);
    std::remove(path.c_str());
    cache.load(T0);
    EXPECT_EQ(cache.size(), 0u);
}

TEST(TileCache, SaveKeepsNewestSixtyFour) {
    const std::string path = tempPath("cap");
    std::remove(path.c_str());
    {
        TileCache cache(path);
        for (int i = 0; i < 70; ++i) cache.store(Cell{i, 0}, {at(i, 0, 0)}, T0 + i);
    }
    TileCache warm(path);
    warm.load(T0 + 70);
    EXPECT_EQ(warm.size(), TileCache::maxEntries);
    EXPECT_FALSE(warm.isServable(Cell{0, 0}, T0 + 70));
    EXPECT_TRUE(warm.isServable(Cell{69, 0}, T0 + 70));
    std::remove(path.c_str());
}

TEST(Core, PublishRanksAndAttachesPins) {
    Core core("");
    core.pins = {{"Happy Lemon", 37.3372, -122.0075, "2580"}};
    const Cell c = Cell::of(37.3349, -122.0090);
    core.cache.store(c, {at(2, 37.3335, -122.0045, "Kaiser"), at(1, 37.3372, -122.0075, "Happy Lemon")}, T0);
    const auto arr = nlohmann::json::parse(core.publishJson(37.3349, -122.0090, T0));
    ASSERT_EQ(arr.size(), 2u);
    EXPECT_EQ(arr[0]["id"], 1);
    EXPECT_EQ(arr[0]["pin"], "2580");
    EXPECT_EQ(arr[0]["accessSpoken"], "Door code 2 5 8 0");
    EXPECT_EQ(arr[0]["distanceText"], "0.2 mi");
    EXPECT_TRUE(arr[1]["pin"].is_null());
    EXPECT_TRUE(nlohmann::json::parse(core.publishJson(0, 0, T0)).empty());
}

TEST(TileCache, MemoryOnlyNeverTouchesDisk) {
    const std::string path = tempPath("memonly");
    std::remove(path.c_str());
    TileCache cache("");
    cache.store(Cell{1, 1}, {at(1, 0, 0)}, T0);
    cache.save();
    std::ifstream in(path);
    EXPECT_FALSE(in.good());
    EXPECT_EQ(cache.size(), 1u);
}
