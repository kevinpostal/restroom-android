#include "restroom/core.h"

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

using namespace restroom;

namespace {

const char* kRefuge = R"([
  {"id":2755,"name":"Me Bar/Sky Bar ","street":"17 W. 32nd St.","city":"New York","state":"NY",
   "accessible":false,"unisex":true,"directions":"top floor!","comment":"rooftop bar",
   "latitude":40.7478833,"longitude":-73.9864795,"created_at":"2014-02-02T20:52:55.055Z",
   "updated_at":"2014-02-02T20:52:55.055Z","downvote":1,"upvote":0,"country":"US",
   "changing_table":false,"edit_id":2755,"approved":true,"distance":0.054,"bearing":"236.46"},
  {"id":9,"name":"Park","street":"","city":"Oakland","state":"CA","accessible":true,"unisex":false,
   "directions":"","comment":"","latitude":37.8,"longitude":-122.27,"downvote":0,"upvote":3,
   "country":"US","changing_table":true,"approved":true,"distance":1.5,"bearing":"10"}
])";

/// Verbatim shape from the live PottyPins `GET /api/posts` dump.
const char* kPottyPins = R"([
  {"id": 12, "name": "Chick-fil-A", "address": "3401 S Bristol St, Santa Ana, CA",
   "latitude": 33.6998222, "longitude": -117.8850177, "imageUrl": "https://pottypins.com/x.jpg",
   "hoursData": {"mon": "6:30-22:00"},
   "restrooms": [
     {"id": 31, "name": "Men's", "pin": "9999"},
     {"id": 32, "name": "Women's", "pin": "1234"}
   ]},
  {"id": 13, "name": "McDonald's", "address": "Irvine, CA",
   "latitude": 33.6949323, "longitude": -117.7998092, "imageUrl": null, "hoursData": null,
   "restrooms": [{"id": 40, "name": "Unisex", "pin": null}]},
  {"id": 14, "name": "Carl’s Jr.", "address": null, "latitude": null, "longitude": null,
   "restrooms": [{"id": 50, "name": "Unisex", "pin": "0000"}]}
])";

const char* kOverpass = R"({"version":0.6,"elements":[
  {"type":"node","id":101,"lat":37.33,"lon":-122.015,"tags":{"leisure":"park","name":"Memorial Park"}},
  {"type":"way","id":202,"center":{"lat":37.34,"lon":-122.02},"tags":{"tourism":"camp_site","name":"Creek Camp",
   "addr:housenumber":"1","addr:street":"Creek Rd","addr:city":"Cupertino","addr:state":"CA"}},
  {"type":"way","id":303,"center":{"lat":37.35,"lon":-122.03},"tags":{"leisure":"park"}}
]})";

}  // namespace

TEST(Refuge, DecodesLiveShape) {
    const auto list = parseRefuge(kRefuge);
    ASSERT_EQ(list.size(), 2u);
    const auto& bar = list[0];
    EXPECT_FALSE(bar.changingTable);
    EXPECT_TRUE(bar.unisex);
    ASSERT_TRUE(bar.distanceMiles);
    EXPECT_NEAR(*bar.distanceMiles * 1609.344, 86.9, 0.1);
    EXPECT_EQ(bar.addressLine(), "17 W. 32nd St., New York, NY");
    EXPECT_EQ(bar.downvote, 1);
    EXPECT_EQ(bar.kind, Kind::Restroom);
    EXPECT_EQ(list[1].addressLine(), "Oakland, CA");
    EXPECT_TRUE(list[1].accessible);
    EXPECT_TRUE(list[1].changingTable);
}

TEST(Refuge, MissingKeysDefaultAndUnapprovedDropped) {
    const auto list = parseRefuge(R"([{"id":1,"latitude":1.0,"longitude":2.0},
                                      {"id":2,"latitude":1.0,"longitude":2.0,"approved":false},
                                      {"id":3,"latitude":1.0,"longitude":2.0,"name":null,"distance":null}])");
    ASSERT_EQ(list.size(), 2u);
    EXPECT_EQ(list[0].id, 1);
    EXPECT_EQ(list[0].name, "");
    EXPECT_FALSE(list[0].accessible);
    EXPECT_EQ(list[0].upvote, 0);
    EXPECT_FALSE(list[0].distanceMiles);
    EXPECT_EQ(list[1].id, 3);
}

TEST(Refuge, RejectsBadShapes) {
    EXPECT_THROW(parseRefuge("{}"), std::runtime_error);
    EXPECT_THROW(parseRefuge("not json"), std::runtime_error);
    EXPECT_THROW(parseRefuge(R"([{"id":"x","latitude":1,"longitude":2}])"), std::runtime_error);
    EXPECT_THROW(parseRefuge(R"([{"id":1,"latitude":1,"longitude":2,"name":5}])"), std::runtime_error);
    EXPECT_THROW(parseRefuge(R"([{"id":1,"longitude":2}])"), std::runtime_error);
}

TEST(PottyPins, FlattenKeepsOnlyPinnedRooms) {
    const auto pins = parsePottyPins(kPottyPins);
    const std::vector<DoorPin> expected = {
        {"Chick-fil-A", 33.6998222, -117.8850177, "9999"},
        {"Chick-fil-A", 33.6998222, -117.8850177, "1234"},
    };
    EXPECT_EQ(pins, expected);
}

TEST(PottyPins, MissingRestroomsArrayIsEmpty) {
    EXPECT_TRUE(parsePottyPins(R"([{"name":"X","latitude":1,"longitude":2}])").empty());
    EXPECT_TRUE(parsePottyPins(R"([{"name":"X","latitude":1,"longitude":2,"restrooms":[{"pin":"   "}]}])").empty());
}

TEST(Overpass, ParksAndCampgroundsWithNegativeIds) {
    const auto list = parseOverpass(kOverpass);
    ASSERT_EQ(list.size(), 2u);
    EXPECT_EQ(list[0].id, -101);
    EXPECT_EQ(list[0].name, "Memorial Park");
    EXPECT_EQ(list[0].kind, Kind::Park);
    EXPECT_DOUBLE_EQ(list[0].lat, 37.33);
    EXPECT_EQ(list[1].id, -(1'000'000'000'000LL + 202));
    EXPECT_EQ(list[1].kind, Kind::Campground);
    EXPECT_DOUBLE_EQ(list[1].lon, -122.02);
    EXPECT_EQ(list[1].addressLine(), "1 Creek Rd, Cupertino, CA");
    EXPECT_STREQ(kindTag(list[1].kind), "Campground");
    EXPECT_EQ(kindTag(Kind::Restroom), nullptr);
}

TEST(Overpass, EmptyOrMissingElements) {
    EXPECT_TRUE(parseOverpass("{}").empty());
    EXPECT_TRUE(parseOverpass(R"({"elements":[]})").empty());
    EXPECT_THROW(parseOverpass("[]"), std::runtime_error);
}

TEST(Place, DistanceText) {
    Place p;
    EXPECT_FALSE(p.distanceText());
    p.distanceMiles = 0.0625;   // 330 ft
    EXPECT_EQ(*p.distanceText(), "330 ft");
    EXPECT_EQ(*p.distanceSpoken(), "330 feet");
    p.distanceMiles = 0.44;
    EXPECT_EQ(*p.distanceText(), "0.4 mi");
    EXPECT_EQ(*p.distanceSpoken(), "0.4 miles");
}

TEST(Json, PublishCarriesDerivedFields) {
    Place p;
    p.id = 7;
    p.name = "Starbucks";
    p.street = "1 Main";
    p.city = "NYC";
    p.comment = "There's a code: 125";
    p.distanceMiles = 0.26;
    p.lat = 40.7; p.lon = -74.0;
    const auto arr = nlohmann::json::parse(toJson({p}));
    ASSERT_EQ(arr.size(), 1u);
    const auto& j = arr[0];
    EXPECT_EQ(j["accessTitle"], "Code 125");
    EXPECT_EQ(j["accessSpoken"], "Door code 1 2 5");
    EXPECT_EQ(j["accessCode"], "125");
    EXPECT_TRUE(j["kindTag"].is_null());
    EXPECT_EQ(j["addressLine"], "1 Main, NYC");
    EXPECT_EQ(j["distanceText"], "0.3 mi");
    EXPECT_EQ(j["distanceSpoken"], "0.3 miles");
    EXPECT_TRUE(j["pin"].is_null());
    EXPECT_EQ(j["kind"], "restroom");
    EXPECT_EQ(j["lat"], 40.7);
    EXPECT_EQ(j["changingTable"], false);
}
