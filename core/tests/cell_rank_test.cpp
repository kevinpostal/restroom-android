#include "restroom/core.h"

#include <gtest/gtest.h>

using namespace restroom;

namespace {
Place at(long long id, double lat, double lon) {
    Place p;
    p.id = id;
    p.lat = lat;
    p.lon = lon;
    return p;
}
}  // namespace

TEST(Cell, RoundsToHundredthsOfADegree) {
    const Cell a = Cell::of(37.33, -122.0);
    EXPECT_EQ(a, (Cell{3733, -12200}));
    EXPECT_EQ(Cell::of(37.3315, -121.9995), a);   // same cell
    EXPECT_NE(Cell::of(37.3315, -122.0055), a);   // next cell west
    EXPECT_DOUBLE_EQ(a.centerLat(), 37.33);
    EXPECT_DOUBLE_EQ(a.centerLon(), -122.0);
}

TEST(Cell, RingOrderEdgesBeforeCorners) {
    const auto ring = Cell{0, 0}.ring();
    const std::array<Cell, 8> expected = {Cell{1, 0}, Cell{-1, 0}, Cell{0, 1}, Cell{0, -1},
                                          Cell{1, 1}, Cell{1, -1}, Cell{-1, 1}, Cell{-1, -1}};
    EXPECT_EQ(ring, expected);
}

TEST(Geo, Haversine) {
    EXPECT_NEAR(haversineMeters(37.3349, -122.0090, 37.3372, -122.0075), 288, 5);
    EXPECT_DOUBLE_EQ(haversineMeters(1, 2, 1, 2), 0);
}

TEST(Rank, RecomputesDistanceAndOrders) {
    std::vector<Place> pool = {at(2, 37.3335, -122.0045), at(1, 37.3372, -122.0075), at(3, 37.3380, -122.0050)};
    const auto out = rank(pool, 37.3349, -122.0090);
    ASSERT_EQ(out.size(), 3u);
    EXPECT_EQ(out[0].id, 1);
    EXPECT_EQ(out[1].id, 2);
    EXPECT_EQ(out[2].id, 3);
    EXPECT_NEAR(*out[0].distanceMiles, 0.18, 0.01);
    EXPECT_NEAR(*out[1].distanceMiles, 0.27, 0.01);
}

TEST(Rank, CapsAtFifty) {
    std::vector<Place> pool;
    for (int i = 0; i < 80; ++i) pool.push_back(at(i, 37.0 + i * 0.001, -122.0));
    const auto out = rank(pool, 37.0, -122.0);
    ASSERT_EQ(out.size(), 50u);
    EXPECT_EQ(out[0].id, 0);
    EXPECT_EQ(out[49].id, 49);
}

TEST(AttachPins, Within75mAttachesNearest) {
    const std::vector<DoorPin> pins = {
        {"Far", 37.3372 + 0.0027, -122.0075, "0000"},   // ~300 m north
        {"Near", 37.3372 + 0.00018, -122.0075, "4321"},  // ~20 m north
        {"Nearer", 37.3372 + 0.00009, -122.0075, "9999"},  // ~10 m north
    };
    const auto out = attachPins({at(1, 37.3372, -122.0075), at(2, 37.3335, -122.0045)}, pins);
    ASSERT_EQ(out.size(), 2u);
    ASSERT_TRUE(out[0].pin);
    EXPECT_EQ(*out[0].pin, "9999");
    EXPECT_FALSE(out[1].pin);
    EXPECT_EQ(accessFor(out[0])->title(), "Code 9999");
}

TEST(AttachPins, TiesKeepPinOrder) {
    const std::vector<DoorPin> pins = {{"A", 1, 2, "1111"}, {"B", 1, 2, "2222"}};
    const auto out = attachPins({at(1, 1, 2)}, pins);
    EXPECT_EQ(*out[0].pin, "1111");
}
