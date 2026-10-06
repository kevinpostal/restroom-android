#include "restroom/core.h"

#include <algorithm>
#include <cmath>
#include <limits>

namespace restroom {

const char* kindTag(Kind kind) {
    switch (kind) {
    case Kind::Restroom: return nullptr;
    case Kind::Park: return "Park";
    case Kind::Campground: return "Campground";
    }
    return nullptr;
}

double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    constexpr double R = 6371008.8;   // mean Earth radius, metres
    constexpr double toRad = M_PI / 180.0;
    const double dLat = (lat2 - lat1) * toRad;
    const double dLon = (lon2 - lon1) * toRad;
    const double a = std::sin(dLat / 2) * std::sin(dLat / 2) +
                     std::cos(lat1 * toRad) * std::cos(lat2 * toRad) * std::sin(dLon / 2) * std::sin(dLon / 2);
    return 2 * R * std::atan2(std::sqrt(a), std::sqrt(1 - a));
}

Cell Cell::of(double lat, double lon) {
    return Cell{static_cast<int>(std::lround(lat / size)), static_cast<int>(std::lround(lon / size))};
}

std::array<Cell, 8> Cell::ring() const {
    return {Cell{x + 1, y}, Cell{x - 1, y}, Cell{x, y + 1}, Cell{x, y - 1},
            Cell{x + 1, y + 1}, Cell{x + 1, y - 1}, Cell{x - 1, y + 1}, Cell{x - 1, y - 1}};
}

std::vector<Place> rank(std::vector<Place> pool, double lat, double lon) {
    for (auto& p : pool) p.distanceMiles = haversineMeters(p.lat, p.lon, lat, lon) / 1609.344;
    std::stable_sort(pool.begin(), pool.end(), [](const Place& a, const Place& b) {
        constexpr double inf = std::numeric_limits<double>::infinity();
        return a.distanceMiles.value_or(inf) < b.distanceMiles.value_or(inf);
    });
    if (pool.size() > 50) pool.resize(50);
    return pool;
}

std::vector<Place> attachPins(std::vector<Place> list, const std::vector<DoorPin>& pins) {
    for (auto& place : list) {
        const DoorPin* best = nullptr;
        double bestMeters = 0;
        for (const auto& pin : pins) {
            const double d = haversineMeters(pin.lat, pin.lon, place.lat, place.lon);
            if (d <= pinMatchMeters && (!best || d < bestMeters)) { best = &pin; bestMeters = d; }
        }
        if (best) place.pin = best->pin;
    }
    return list;
}

}  // namespace restroom
