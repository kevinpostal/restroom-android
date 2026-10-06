#include "json_internal.h"

#include <algorithm>
#include <cstdio>
#include <fstream>
#include <set>
#include <sstream>

namespace restroom {

using nlohmann::json;

TileCache::TileCache(std::string filePath) : path_(std::move(filePath)) {}

std::optional<long> TileCache::age(Cell cell, long nowSec) const {
    auto it = entries_.find(cell);
    if (it == entries_.end()) return std::nullopt;
    return nowSec - it->second.at;
}

bool TileCache::isFresh(Cell cell, long nowSec) const {
    auto a = age(cell, nowSec);
    return a && *a < freshTTL;
}

bool TileCache::isServable(Cell cell, long nowSec) const {
    auto a = age(cell, nowSec);
    return a && *a < serveTTL;
}

bool TileCache::ringServable(Cell cell, long nowSec) const {
    for (const auto& c : cell.ring()) {
        if (isServable(c, nowSec)) return true;
    }
    return false;
}

std::vector<Cell> TileCache::missingRing(Cell cell, long nowSec) const {
    std::vector<Cell> out;
    for (const auto& c : cell.ring()) {
        if (!isFresh(c, nowSec)) out.push_back(c);
    }
    return out;
}

std::vector<Place> TileCache::pool(Cell cell, long nowSec) const {
    std::vector<Place> out;
    std::set<long long> seen;
    const auto ring = cell.ring();
    std::array<Cell, 9> order{cell, ring[0], ring[1], ring[2], ring[3], ring[4], ring[5], ring[6], ring[7]};
    for (const auto& c : order) {
        if (!isServable(c, nowSec)) continue;
        for (const auto& p : entries_.at(c).list) {
            if (seen.insert(p.id).second) out.push_back(p);
        }
    }
    return out;
}

void TileCache::store(Cell cell, std::vector<Place> page, long nowSec) {
    for (auto it = entries_.begin(); it != entries_.end();) {
        if (nowSec - it->second.at >= serveTTL) it = entries_.erase(it); else ++it;
    }
    entries_[cell] = Entry{nowSec, std::move(page)};
    save();
}

void TileCache::erase(Cell cell) { entries_.erase(cell); }

void TileCache::load(long nowSec) {
    entries_.clear();
    if (path_.empty()) return;
    std::ifstream in(path_, std::ios::binary);
    if (!in) return;
    std::stringstream buf;
    buf << in.rdbuf();
    const json raw = json::parse(buf.str(), nullptr, false);
    if (raw.is_discarded() || !raw.is_object()) return;
    try {
        for (const auto& [key, value] : raw.items()) {
            int x = 0, y = 0;
            if (std::sscanf(key.c_str(), "%d,%d", &x, &y) != 2) continue;
            if (!value.is_object() || !value.contains("at") || !value.contains("list")) continue;
            const long at = value.at("at").get<long>();
            if (nowSec - at >= serveTTL) continue;
            Entry entry{at, {}};
            for (const auto& row : value.at("list")) entry.list.push_back(detail::placeFromJson(row));
            entries_[Cell{x, y}] = std::move(entry);
        }
    } catch (const std::exception&) {
        entries_.clear();   // corrupt file: start cold rather than half-warm
    }
}

void TileCache::save() const {
    if (path_.empty()) return;
    std::vector<std::pair<Cell, const Entry*>> newest;
    newest.reserve(entries_.size());
    for (const auto& [cell, entry] : entries_) newest.emplace_back(cell, &entry);
    std::stable_sort(newest.begin(), newest.end(), [](const auto& a, const auto& b) { return a.second->at > b.second->at; });
    if (newest.size() > maxEntries) newest.resize(maxEntries);

    json raw = json::object();
    for (const auto& [cell, entry] : newest) {
        json list = json::array();
        for (const auto& p : entry->list) list.push_back(detail::placeToJson(p));
        raw[std::to_string(cell.x) + "," + std::to_string(cell.y)] = json{{"at", entry->at}, {"list", std::move(list)}};
    }
    const std::string tmp = path_ + ".tmp";
    {
        std::ofstream out(tmp, std::ios::binary | std::ios::trunc);
        if (!out) return;
        out << raw.dump();
        if (!out) return;
    }
    if (std::rename(tmp.c_str(), path_.c_str()) != 0) std::remove(tmp.c_str());
}

std::string Core::publishJson(double lat, double lon, long nowSec) const {
    return toJson(attachPins(rank(cache.pool(Cell::of(lat, lon), nowSec), lat, lon), pins));
}

}  // namespace restroom
