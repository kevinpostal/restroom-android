// Restroom core: parsers, door-code heuristics, grid tile cache, ranking and pin matching.
// Pure C++17, no Android dependencies; the Kotlin shell only renders what this publishes.
#pragma once

#include <array>
#include <cstddef>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace restroom {

enum class Kind { Restroom, Park, Campground };

/// Row/card tag for non-restroom places; nullptr for a verified restroom.
const char* kindTag(Kind kind);

struct Place {
    long long id = 0;
    std::string name, street, city, state, directions, comment;
    bool accessible = false, unisex = false, changingTable = false;
    double lat = 0, lon = 0;
    int upvote = 0, downvote = 0;
    std::optional<double> distanceMiles;
    Kind kind = Kind::Restroom;
    /// PottyPins door pin attached by `attachPins`; never parsed from Refuge.
    std::optional<std::string> pin;

    /// "street, city, state" from the non-empty, trimmed parts.
    std::string addressLine() const;
    /// < 0.1 mi → feet to the nearest 10 ("330 ft"); else miles to one decimal ("0.4 mi").
    std::optional<std::string> distanceText() const;
    /// `distanceText` with units spelled out ("330 feet", "0.4 miles").
    std::optional<std::string> distanceSpoken() const;
};

/// How to get through a locked restroom door. From a PottyPins pin or Refuge free text.
struct Access {
    enum class Type { Code, OnReceipt, AskStaff, Open };
    Type type;
    std::string code;   // digits to punch when type == Code

    std::string title() const;    // "Code 125" | "Code on receipt" | "Ask staff for code" | "No code needed"
    std::string spoken() const;   // "Door code 1 2 5" when keypad-only, "Door code <code>" otherwise; others = title()

    bool operator==(const Access& o) const { return type == o.type && code == o.code; }
    bool operator!=(const Access& o) const { return !(*this == o); }
};

/// Heuristic over Refuge `directions` + `comment`. Order: negation → digits → receipt → ask/bare mention.
std::optional<Access> parseAccess(std::string_view text);
/// PottyPins `pin` strings: digits (optionally with a trailing note), or "No PIN required".
std::optional<Access> accessFromPin(std::string_view pin);
/// A PottyPins pin wins over anything mined from Refuge text.
std::optional<Access> accessFor(const Place& place);

struct DoorPin {
    std::string name;
    double lat = 0, lon = 0;
    std::string pin;
    bool operator==(const DoorPin& o) const { return name == o.name && lat == o.lat && lon == o.lon && pin == o.pin; }
};

/// Refuge `by_location` array. Missing keys default; `approved == false` rows are dropped.
/// Throws std::runtime_error on a non-array or wrong field types.
std::vector<Place> parseRefuge(std::string_view json);
/// PottyPins `/api/posts` dump flattened to one pin per room; null coordinates and blank pins are skipped.
std::vector<DoorPin> parsePottyPins(std::string_view json);
/// Overpass `out center` elements: parks and camp sites with a name. Ids are negated so they never collide with Refuge.
std::vector<Place> parseOverpass(std::string_view json);

double haversineMeters(double lat1, double lon1, double lat2, double lon2);

/// ~1.1 km grid cell (0.01°) used as the results cache key; cells are centred on multiples of 0.01°.
struct Cell {
    int x = 0, y = 0;
    static constexpr double size = 0.01;
    static Cell of(double lat, double lon);
    double centerLat() const { return x * size; }
    double centerLon() const { return y * size; }
    /// The 8 surrounding cells, nearest-first for prefetch order (edges before corners).
    std::array<Cell, 8> ring() const;
    bool operator==(const Cell& o) const { return x == o.x && y == o.y; }
    bool operator!=(const Cell& o) const { return !(*this == o); }
    bool operator<(const Cell& o) const { return x != o.x ? x < o.x : y < o.y; }
};

/// Nearest 50 by straight-line distance from (lat, lon), with `distanceMiles` recomputed to match.
std::vector<Place> rank(std::vector<Place> pool, double lat, double lon);
/// Copies each place with the nearest pin within 75 m; ties keep PottyPins order.
std::vector<Place> attachPins(std::vector<Place> list, const std::vector<DoorPin>& pins);
constexpr double pinMatchMeters = 75;

/// Pages of nearest results keyed by grid cell, persisted as one JSON file so a relaunch starts warm.
/// Fresh (< 10 min) pages are final; older ones up to a day are served at once and refetched behind them.
class TileCache {
public:
    static constexpr long freshTTL = 10 * 60;
    static constexpr long serveTTL = 24 * 60 * 60;
    static constexpr std::size_t maxEntries = 64;

    /// Empty path = memory only.
    explicit TileCache(std::string filePath);

    /// Reads the file, dropping entries older than serveTTL. Missing/corrupt file = empty cache.
    void load(long nowSec);
    /// Writes the newest maxEntries atomically (tmp + rename); failures are ignored.
    void save() const;

    void store(Cell cell, std::vector<Place> page, long nowSec);
    void erase(Cell cell);
    bool isFresh(Cell cell, long nowSec) const;
    bool isServable(Cell cell, long nowSec) const;
    bool ringServable(Cell cell, long nowSec) const;
    /// Ring cells that are not fresh, in ring order.
    std::vector<Cell> missingRing(Cell cell, long nowSec) const;
    /// Union of servable pages for the centre and its ring, deduplicated by id, centre first.
    std::vector<Place> pool(Cell cell, long nowSec) const;
    std::size_t size() const { return entries_.size(); }

private:
    struct Entry { long at; std::vector<Place> list; };
    std::string path_;
    std::map<Cell, Entry> entries_;
    std::optional<long> age(Cell cell, long nowSec) const;
};

/// JSON array of places with the derived display fields the shell needs.
std::string toJson(const std::vector<Place>& list);

/// What JNI wraps; one instance per process.
struct Core {
    TileCache cache;
    std::vector<DoorPin> pins;

    explicit Core(std::string cachePath) : cache(std::move(cachePath)) {}
    /// attachPins(rank(cache.pool(Cell::of(lat, lon)), lat, lon)) as JSON.
    std::string publishJson(double lat, double lon, long nowSec) const;
};

}  // namespace restroom
