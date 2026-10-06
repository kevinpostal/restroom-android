#include "json_internal.h"

#include <cctype>
#include <cmath>
#include <cstdio>
#include <stdexcept>

namespace restroom {

using nlohmann::json;

namespace {

std::string trimmed(const std::string& s) {
    std::size_t b = 0, e = s.size();
    while (b < e && std::isspace(static_cast<unsigned char>(s[b]))) ++b;
    while (e > b && std::isspace(static_cast<unsigned char>(s[e - 1]))) --e;
    return s.substr(b, e - b);
}

[[noreturn]] void bad(const std::string& what) { throw std::runtime_error("Couldn't read restroom data: " + what); }

/// Swift `decodeIfPresent` semantics: absent or null → default; wrong type → error.
std::string str(const json& o, const char* key, const std::string& fallback = {}) {
    auto it = o.find(key);
    if (it == o.end() || it->is_null()) return fallback;
    if (!it->is_string()) bad(std::string(key) + " is not a string");
    return it->get<std::string>();
}
bool boolean(const json& o, const char* key, bool fallback) {
    auto it = o.find(key);
    if (it == o.end() || it->is_null()) return fallback;
    if (!it->is_boolean()) bad(std::string(key) + " is not a bool");
    return it->get<bool>();
}
int integer(const json& o, const char* key, int fallback) {
    auto it = o.find(key);
    if (it == o.end() || it->is_null()) return fallback;
    if (!it->is_number()) bad(std::string(key) + " is not a number");
    return it->get<int>();
}
std::optional<double> number(const json& o, const char* key) {
    auto it = o.find(key);
    if (it == o.end() || it->is_null()) return std::nullopt;
    if (!it->is_number()) bad(std::string(key) + " is not a number");
    return it->get<double>();
}
double requiredNumber(const json& o, const char* key) {
    auto v = number(o, key);
    if (!v) bad(std::string("missing ") + key);
    return *v;
}

json parse(std::string_view text) {
    json j = json::parse(text, nullptr, false);
    if (j.is_discarded()) bad("malformed JSON");
    return j;
}

/// One Refuge row; `approved` is reported so the caller can drop unapproved rows.
Place refugePlace(const json& o, bool& approved) {
    if (!o.is_object()) bad("row is not an object");
    Place p;
    auto id = o.find("id");
    if (id == o.end() || !id->is_number_integer()) bad("missing id");
    p.id = id->get<long long>();
    p.name = str(o, "name");
    p.street = str(o, "street");
    p.city = str(o, "city");
    p.state = str(o, "state");
    p.accessible = boolean(o, "accessible", false);
    p.unisex = boolean(o, "unisex", false);
    p.changingTable = boolean(o, "changing_table", false);
    p.directions = str(o, "directions");
    p.comment = str(o, "comment");
    p.lat = requiredNumber(o, "latitude");
    p.lon = requiredNumber(o, "longitude");
    p.upvote = integer(o, "upvote", 0);
    p.downvote = integer(o, "downvote", 0);
    approved = boolean(o, "approved", true);
    p.distanceMiles = number(o, "distance");
    p.kind = detail::kindFromName(str(o, "kind", "restroom"));
    return p;
}

std::string feet(double miles) {
    const double ft = std::round(miles * 5280 / 10) * 10;
    char buf[32];
    std::snprintf(buf, sizeof buf, "%d ft", static_cast<int>(ft));
    return buf;
}

}  // namespace

std::string Place::addressLine() const {
    std::string out;
    for (const auto* part : {&street, &city, &state}) {
        const std::string t = trimmed(*part);
        if (t.empty()) continue;
        if (!out.empty()) out += ", ";
        out += t;
    }
    return out;
}

std::optional<std::string> Place::distanceText() const {
    if (!distanceMiles) return std::nullopt;
    const double miles = *distanceMiles;
    if (miles < 0.1) return feet(miles);
    char buf[32];
    std::snprintf(buf, sizeof buf, "%.1f mi", miles);
    return std::string(buf);
}

std::optional<std::string> Place::distanceSpoken() const {
    auto t = distanceText();
    if (!t) return std::nullopt;
    const auto ends = [&](const char* suffix) {
        const std::string s(suffix);
        return t->size() >= s.size() && t->compare(t->size() - s.size(), s.size(), s) == 0;
    };
    if (ends(" mi")) return t->substr(0, t->size() - 3) + " miles";
    if (ends(" ft")) return t->substr(0, t->size() - 3) + " feet";
    return t;
}

namespace detail {

const char* kindName(Kind kind) {
    switch (kind) {
    case Kind::Restroom: return "restroom";
    case Kind::Park: return "park";
    case Kind::Campground: return "campground";
    }
    return "restroom";
}

Kind kindFromName(std::string_view name) {
    if (name == "park") return Kind::Park;
    if (name == "campground") return Kind::Campground;
    if (name == "restroom") return Kind::Restroom;
    bad("unknown kind");
}

json placeToJson(const Place& p) {
    json j = {
        {"id", p.id}, {"name", p.name}, {"street", p.street}, {"city", p.city}, {"state", p.state},
        {"accessible", p.accessible}, {"unisex", p.unisex}, {"changing_table", p.changingTable},
        {"directions", p.directions}, {"comment", p.comment},
        {"latitude", p.lat}, {"longitude", p.lon},
        {"upvote", p.upvote}, {"downvote", p.downvote},
        {"kind", kindName(p.kind)},
    };
    if (p.distanceMiles) j["distance"] = *p.distanceMiles;
    return j;
}

Place placeFromJson(const json& j) {
    bool approved = true;
    return refugePlace(j, approved);
}

}  // namespace detail

std::vector<Place> parseRefuge(std::string_view text) {
    const json j = parse(text);
    if (!j.is_array()) bad("expected an array");
    std::vector<Place> out;
    out.reserve(j.size());
    for (const auto& row : j) {
        bool approved = true;
        Place p = refugePlace(row, approved);
        if (approved) out.push_back(std::move(p));
    }
    return out;
}

std::vector<DoorPin> parsePottyPins(std::string_view text) {
    const json j = parse(text);
    if (!j.is_array()) bad("expected an array");
    std::vector<DoorPin> out;
    for (const auto& loc : j) {
        if (!loc.is_object()) bad("location is not an object");
        const auto lat = number(loc, "latitude");
        const auto lon = number(loc, "longitude");
        if (!lat || !lon) continue;
        const std::string name = str(loc, "name");
        auto rooms = loc.find("restrooms");
        if (rooms == loc.end() || rooms->is_null()) continue;
        if (!rooms->is_array()) bad("restrooms is not an array");
        for (const auto& room : *rooms) {
            if (!room.is_object()) bad("restroom is not an object");
            const std::string pin = trimmed(str(room, "pin"));
            if (pin.empty()) continue;
            out.push_back(DoorPin{name, *lat, *lon, pin});
        }
    }
    return out;
}

std::vector<Place> parseOverpass(std::string_view text) {
    const json j = parse(text);
    if (!j.is_object()) bad("expected an object");
    auto elements = j.find("elements");
    if (elements == j.end() || !elements->is_array()) return {};
    std::vector<Place> out;
    for (const auto& el : *elements) {
        if (!el.is_object()) continue;
        auto tags = el.find("tags");
        if (tags == el.end() || !tags->is_object()) continue;
        const std::string name = trimmed(str(*tags, "name"));
        if (name.empty()) continue;
        const json* coords = &el;
        if (!el.contains("lat")) {
            auto center = el.find("center");
            if (center == el.end() || !center->is_object()) continue;
            coords = &*center;
        }
        const auto lat = number(*coords, "lat");
        const auto lon = number(*coords, "lon");
        if (!lat || !lon) continue;
        auto id = el.find("id");
        if (id == el.end() || !id->is_number_integer()) continue;
        Place p;
        const long long raw = id->get<long long>();
        p.id = str(el, "type") == "node" ? -raw : -(1'000'000'000'000LL + raw);
        p.name = name;
        const std::string house = str(*tags, "addr:housenumber"), road = str(*tags, "addr:street");
        p.street = road.empty() ? "" : (house.empty() ? road : house + " " + road);
        p.city = str(*tags, "addr:city");
        p.state = str(*tags, "addr:state");
        p.lat = *lat;
        p.lon = *lon;
        p.kind = str(*tags, "tourism") == "camp_site" ? Kind::Campground : Kind::Park;
        out.push_back(std::move(p));
    }
    return out;
}

std::string toJson(const std::vector<Place>& list) {
    json arr = json::array();
    for (const auto& p : list) {
        json j = {
            {"id", p.id}, {"name", p.name}, {"street", p.street}, {"city", p.city}, {"state", p.state},
            {"directions", p.directions}, {"comment", p.comment},
            {"accessible", p.accessible}, {"unisex", p.unisex}, {"changingTable", p.changingTable},
            {"lat", p.lat}, {"lon", p.lon}, {"upvote", p.upvote}, {"downvote", p.downvote},
            {"kind", detail::kindName(p.kind)},
        };
        j["distanceMiles"] = p.distanceMiles ? json(*p.distanceMiles) : json(nullptr);
        j["pin"] = p.pin ? json(*p.pin) : json(nullptr);
        const auto access = accessFor(p);
        j["accessTitle"] = access ? json(access->title()) : json(nullptr);
        j["accessSpoken"] = access ? json(access->spoken()) : json(nullptr);
        j["accessCode"] = access && access->type == Access::Type::Code ? json(access->code) : json(nullptr);
        const char* tag = kindTag(p.kind);
        j["kindTag"] = tag ? json(tag) : json(nullptr);
        j["addressLine"] = p.addressLine();
        const auto text = p.distanceText();
        j["distanceText"] = text ? json(*text) : json(nullptr);
        const auto spoken = p.distanceSpoken();
        j["distanceSpoken"] = spoken ? json(*spoken) : json(nullptr);
        arr.push_back(std::move(j));
    }
    return arr.dump();
}

}  // namespace restroom
