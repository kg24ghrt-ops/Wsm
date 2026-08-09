#pragma once

#include <string>
#include <vector>
#include <memory>

class EditorBuffer {
public:
    EditorBuffer();
    ~EditorBuffer();

    bool loadFile(const std::string& path);
    bool saveFile(const std::string& path);
    void insertText(size_t pos, const std::string& text);
    void deleteText(size_t pos, size_t len);
    std::string getText() const;
    size_t getLength() const;

    // For syntax highlighting
    std::vector<std::pair<int, int>> getTokensForLine(size_t line) const;

private:
    struct Impl;
    std::unique_ptr<Impl> pImpl;
};