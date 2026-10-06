#include "restroom/core.h"

#include <gtest/gtest.h>

using namespace restroom;

namespace {
Access code(const char* c) { return Access{Access::Type::Code, c}; }
const Access onReceipt{Access::Type::OnReceipt, {}};
const Access askStaff{Access::Type::AskStaff, {}};
const Access open{Access::Type::Open, {}};
}  // namespace

/// Live Refuge phrasings observed in Midtown Manhattan results, plus keypad edge cases.
TEST(Access, ParseRefugeText) {
    const std::pair<const char*, std::optional<Access>> cases[] = {
        {"There's a code: 125", code("125")},
        {"Code is 1234#", code("1234#")},
        {"you need a bathroom code, it's on the receipt", onReceipt},
        {"The bathroom code is on the receipt", onReceipt},
        {"You have to ask for a code", askStaff},
        {"must ask for a code", askStaff},
        {"You need to get the bathroom code or buy something", askStaff},
        {"keypad by the door", askStaff},
        {"you don't need a code or anything", std::nullopt},
        {"top floor!", std::nullopt},
        {"", std::nullopt},
    };
    for (const auto& [text, expected] : cases) {
        EXPECT_EQ(parseAccess(text), expected) << text;
    }
}

TEST(Access, FromPin) {
    EXPECT_EQ(accessFromPin("9999"), code("9999"));
    EXPECT_EQ(accessFromPin("2844 (+ ENTER)"), code("2844 (+ ENTER)"));
    EXPECT_EQ(accessFromPin("No PIN required"), open);
    EXPECT_EQ(accessFromPin("  "), std::nullopt);
}

TEST(Access, SpokenSpellsKeypadDigits) {
    EXPECT_EQ(code("125").spoken(), "Door code 1 2 5");
    EXPECT_EQ(code("2844 (+ ENTER)").spoken(), "Door code 2844 (+ ENTER)");
    EXPECT_EQ(onReceipt.spoken(), "Code on receipt");
}

TEST(Access, PinBeatsText) {
    Place r;
    r.id = 1;
    r.name = "A";
    r.comment = "ask for the code";
    EXPECT_EQ(accessFor(r), askStaff);
    r.pin = "7777";
    EXPECT_EQ(accessFor(r), code("7777"));
}

TEST(Access, Titles) {
    EXPECT_EQ(code("125").title(), "Code 125");
    EXPECT_EQ(onReceipt.title(), "Code on receipt");
    EXPECT_EQ(askStaff.title(), "Ask staff for code");
    EXPECT_EQ(open.title(), "No code needed");
}

TEST(Access, NegationAndDigitEdgeCases) {
    EXPECT_EQ(parseAccess("Door is open, without any code"), std::nullopt);
    EXPECT_EQ(parseAccess("Combo 7#31"), code("7#31"));
    EXPECT_EQ(parseAccess("PIN 12"), askStaff);        // too short to be a keypad code
    EXPECT_EQ(parseAccess("code 123456789"), code("12345678"));   // capped at 8 keypad chars
}
