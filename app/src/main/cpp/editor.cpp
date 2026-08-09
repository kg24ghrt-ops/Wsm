#include "editor.h"
#include <fstream>
#include <sstream>
#include <memory>
#include <algorithm>

struct EditorBuffer::Impl {
    std::string buffer;
    size_t gapStart = 0;
    size_t gapEnd = 0;
    static constexpr size_t INITIAL_GAP = 1024;

    Impl() {
        buffer.resize(INITIAL_GAP);
        gapStart = 0;
        gapEnd = INITIAL_GAP;
    }

    void ensureGap(size_t pos, size_t len) {
        size_t needed = len;
        size_t currentGap = gapEnd - gapStart;
        if (currentGap >= needed) return;
        size_t newGap = std::max(needed, currentGap * 2 + 1024);
        size_t oldSize = buffer.size();
        buffer.resize(oldSize + newGap - currentGap);
        std::copy(buffer.begin() + gapEnd, buffer.begin() + oldSize,
                  buffer.begin() + gapEnd + (newGap - currentGap));
        gapEnd = gapStart + newGap;
    }

    void insert(size_t pos, const std::string& text) {
        if (pos > gapStart) {
            size_t moveSize = pos - gapStart;
            std::copy(buffer.begin() + gapEnd, buffer.begin() + gapEnd + moveSize,
                      buffer.begin() + gapStart);
            gapStart += moveSize;
            gapEnd += moveSize;
        } else if (pos < gapStart) {
            size_t moveSize = gapStart - pos;
            std::copy_backward(buffer.begin() + gapStart, buffer.begin() + gapEnd,
                               buffer.begin() + gapEnd - moveSize);
            gapStart -= moveSize;
            gapEnd -= moveSize;
        }
        ensureGap(gapStart, text.size());
        std::copy(text.begin(), text.end(), buffer.begin() + gapStart);
        gapStart += text.size();
    }

    void erase(size_t pos, size_t len) {
        // Simplified: rebuild full string
        std::string full = getFull();
        full.erase(pos, len);
        buffer = full;
        gapStart = full.size();
        gapEnd = buffer.size();
    }

    std::string getFull() const {
        std::string result;
        result.reserve(buffer.size() - (gapEnd - gapStart));
        result.append(buffer.data(), gapStart);
        result.append(buffer.data() + gapEnd, buffer.size() - gapEnd);
        return result;
    }
};

EditorBuffer::EditorBuffer() : pImpl(std::make_unique<Impl>()) {}
EditorBuffer::~EditorBuffer() = default;

bool EditorBuffer::loadFile(const std::string& path) {
    std::ifstream file(path);
    if (!file) return false;
    std::stringstream ss;
    ss << file.rdbuf();
    std::string content = ss.str();
    pImpl->buffer = content;
    pImpl->gapStart = content.size();
    pImpl->gapEnd = content.size();
    return true;
}

bool EditorBuffer::saveFile(const std::string& path) {
    std::ofstream file(path);
    if (!file) return false;
    file << getText();
    return true;
}

void EditorBuffer::insertText(size_t pos, const std::string& text) {
    pImpl->insert(pos, text);
}

void EditorBuffer::deleteText(size_t pos, size_t len) {
    pImpl->erase(pos, len);
}

std::string EditorBuffer::getText() const {
    return pImpl->getFull();
}

size_t EditorBuffer::getLength() const {
    return pImpl->buffer.size() - (pImpl->gapEnd - pImpl->gapStart);
}