#include "restroom/core.h"

#include <cctype>
#include <regex>

namespace restroom {

namespace {

std::string lowered(std::string_view s) {
    std::string out(s);
    for (auto& ch : out) ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    return out;
}

std::string trimmed(std::string_view s) {
    const auto isSpace = [](unsigned char c) { return std::isspace(c) != 0; };
    std::size_t b = 0, e = s.size();
    while (b < e && isSpace(static_cast<unsigned char>(s[b]))) ++b;
    while (e > b && isSpace(static_cast<unsigned char>(s[e - 1]))) --e;
    return std::string(s.substr(b, e - b));
}

bool keypadOnly(const std::string& code) {
    for (unsigned char c : code) {
        if (!(std::isdigit(c) || c == '*' || c == '#')) return false;
    }
    return !code.empty();
}

// Same four patterns as the iOS Access.parse / Access.fromPin.
const std::regex& mentionRe() {
    static const std::regex re(R"(\b(code|combo|combination|keypad|key pad|pin)\b)");
    return re;
}
const std::regex& negationRe() {
    static const std::regex re(R"(\b(no|don.?t|doesn.?t|not|without)\b[^.]{0,20}\b(code|pin|combo)\b)");
    return re;
}
const std::regex& digitsRe() {
    static const std::regex re(R"(\b(code|combo|combination|pin)\b[^0-9\n]{0,12}([0-9][0-9*#]{2,7}))");
    return re;
}
const std::regex& tailDigitsRe() {
    static const std::regex re(R"(([0-9][0-9*#]{2,7})$)");
    return re;
}
const std::regex& openPinRe() {
    static const std::regex re(R"(\b(no pin|none|not required|no code)\b)", std::regex::icase);
    return re;
}

}  // namespace

std::string Access::title() const {
    switch (type) {
    case Type::Code: return "Code " + code;
    case Type::OnReceipt: return "Code on receipt";
    case Type::AskStaff: return "Ask staff for code";
    case Type::Open: return "No code needed";
    }
    return {};
}

std::string Access::spoken() const {
    if (type != Type::Code) return title();
    if (!keypadOnly(code)) return "Door code " + code;
    std::string out = "Door code ";
    for (std::size_t i = 0; i < code.size(); ++i) {
        if (i) out += ' ';
        out += code[i];
    }
    return out;
}

std::optional<Access> parseAccess(std::string_view text) {
    const std::string t = lowered(text);
    if (!std::regex_search(t, mentionRe())) return std::nullopt;
    if (std::regex_search(t, negationRe())) return std::nullopt;
    std::smatch m;
    if (std::regex_search(t, m, digitsRe())) {
        const std::string hit = m.str(0);
        std::smatch tail;
        if (std::regex_search(hit, tail, tailDigitsRe())) return Access{Access::Type::Code, tail.str(1)};
        return Access{Access::Type::Code, m.str(2)};
    }
    if (t.find("receipt") != std::string::npos) return Access{Access::Type::OnReceipt, {}};
    return Access{Access::Type::AskStaff, {}};
}

std::optional<Access> accessFromPin(std::string_view pin) {
    const std::string p = trimmed(pin);
    if (p.empty()) return std::nullopt;
    if (std::regex_search(p, openPinRe())) return Access{Access::Type::Open, {}};
    return Access{Access::Type::Code, p};
}

std::optional<Access> accessFor(const Place& place) {
    if (place.pin) {
        if (auto a = accessFromPin(*place.pin)) return a;
    }
    return parseAccess(place.directions + " " + place.comment);
}

}  // namespace restroom
