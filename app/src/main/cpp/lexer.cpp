#include "lexer.h"
#include <regex>
#include <cctype>

std::vector<std::pair<int, int>> Lexer::tokenize(const std::string& line) {
    std::vector<std::pair<int, int>> tokens;

    std::vector<std::pair<std::regex, int>> rules = {
        {std::regex(R"(^#.*)"), TOKEN_PREPROCESSOR},
        {std::regex(R"(\b(if|else|for|while|return|int|float|double|char|void|class|public|private|static|final)\b)"), TOKEN_KEYWORD},
        {std::regex(R"(\b\d+\b)"), TOKEN_NUMBER},
        {std::regex(R"(\".*?\"|\'.*?\')"), TOKEN_STRING},
        {std::regex(R"(\/\/.*|\/\*.*?\*\/)"), TOKEN_COMMENT},
        {std::regex(R"([+\-*/%=<>!&|]+)"), TOKEN_OPERATOR},
    };

    size_t pos = 0;
    while (pos < line.size()) {
        bool matched = false;
        for (auto& [regex, type] : rules) {
            std::smatch match;
            if (std::regex_search(line.begin() + pos, line.end(), match, regex)) {
                if (match.position() == 0) {
                    tokens.emplace_back(type, match.length());
                    pos += match.length();
                    matched = true;
                    break;
                }
            }
        }
        if (!matched) {
            size_t end = pos;
            while (end < line.size() && !std::isspace(line[end])) end++;
            if (end > pos) {
                tokens.emplace_back(TOKEN_IDENTIFIER, end - pos);
                pos = end;
            } else {
                pos++;
            }
        }
    }
    return tokens;
}