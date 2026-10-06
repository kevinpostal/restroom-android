// Shared Place <-> JSON conversion for the cache file and the publish payload.
#pragma once

#include "restroom/core.h"

#include <nlohmann/json.hpp>

namespace restroom::detail {

/// Stored fields only (id … kind, distanceMiles); never `pin` or derived text.
nlohmann::json placeToJson(const Place& p);
/// Inverse of `placeToJson`; throws nlohmann::json exceptions on wrong types.
Place placeFromJson(const nlohmann::json& j);

const char* kindName(Kind kind);
Kind kindFromName(std::string_view name);

}  // namespace restroom::detail
