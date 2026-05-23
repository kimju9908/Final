import React, { useEffect, useRef, useState, useCallback } from "react";
import SearchApi from "../api/SearchApi";

interface SearchSuggestionProps {
  searchType: "FOOD" | "COCKTAIL";
  query: string;
  onQueryChange: (value: string) => void;
  onSearch: () => void;
  placeholder?: string;
}

const SearchSuggestion: React.FC<SearchSuggestionProps> = ({
  searchType,
  query,
  onQueryChange,
  onSearch,
  placeholder = "Search...",
}) => {
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [suggestionType, setSuggestionType] = useState<"POPULAR" | "AUTOCOMPLETE">("POPULAR");
  const [isOpen, setIsOpen] = useState(false);
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortControllerRef = useRef<AbortController | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const fetchSuggestions = useCallback(
    async (keyword: string) => {
      // 이전 요청 취소
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      const controller = new AbortController();
      abortControllerRef.current = controller;

      try {
        const data = await SearchApi.getSuggestions(searchType, keyword, controller.signal);
        setSuggestions(data.items);
        setSuggestionType(data.type);
      } catch (e: any) {
        if (e?.name === "CanceledError" || e?.name === "AbortError") return;
        setSuggestions([]);
      }
    },
    [searchType]
  );

  // 입력값 변경 시 디바운스 후 API 호출
  useEffect(() => {
    if (!isOpen) return;
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = setTimeout(() => {
      fetchSuggestions(query);
    }, 200);
    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
  }, [query, isOpen, fetchSuggestions]);

  // 언마운트 시 요청 취소
  useEffect(() => {
    return () => {
      abortControllerRef.current?.abort();
    };
  }, []);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleFocus = () => {
    setIsOpen(true);
    fetchSuggestions(query);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      setIsOpen(false);
      onSearch();
    }
  };

  const handleSelectSuggestion = (item: string) => {
    onQueryChange(item);
    setIsOpen(false);
    setTimeout(() => onSearch(), 0);
  };

  return (
    <div ref={containerRef} className="relative flex-1">
      <input
        type="text"
        placeholder={placeholder}
        value={query}
        onChange={(e) => onQueryChange(e.target.value)}
        onFocus={handleFocus}
        onKeyDown={handleKeyDown}
        className="w-full p-2 border border-kakiBrown dark:border-darkKaki rounded md:rounded-r-none focus:outline-none"
      />
      {isOpen && suggestions.length > 0 && (
        <div className="absolute left-0 right-0 top-full z-50 bg-white dark:bg-gray-800 border border-kakiBrown dark:border-darkKaki rounded-b shadow-lg">
          <div className="px-3 py-1 text-xs text-gray-400 dark:text-gray-500 border-b border-gray-100 dark:border-gray-700">
            {suggestionType === "POPULAR" ? "인기 검색어" : "자동완성"}
          </div>
          <ul>
            {suggestions.map((item, idx) => (
              <li
                key={item}
                onMouseDown={() => handleSelectSuggestion(item)}
                className="px-3 py-2 cursor-pointer text-kakiBrown dark:text-softBeige hover:bg-orange-50 dark:hover:bg-gray-700 text-sm flex items-center gap-2"
              >
                {suggestionType === "POPULAR" && (
                  <span className="text-warmOrange dark:text-orange-400 font-bold text-xs w-4 text-center">
                    {idx + 1}
                  </span>
                )}
                {item}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
};

export default SearchSuggestion;
