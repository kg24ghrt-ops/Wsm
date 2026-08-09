#pragma once

#include <string>
#include <vector>
#include <utility>

class Lexer {
public:
    enum TokenType {
        TOKEN_KEYWORD,
        TOKEN_IDENTIFIER,
        TOKEN_NUMBER,
        TOKEN_STRING,
        TOKEN_COMMENT,
        TOKEN_OPERATOR,
        TOKEN_PREPROCESSOR,
        TOKEN_DEFAULT
    };

    static std::vector<std::pair<int, int>> tokenize(const std::string& line);
};