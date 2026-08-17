import React, { useState, useRef, useEffect } from "react";
import ChatApi, { ChatRecipe } from "../api/ChatApi";

interface Message {
  role: "user" | "bot";
  text: string;
  recipes?: ChatRecipe[];
  ingredients?: string[];
}

interface RecipeChatBotProps {
  type?: "food" | "cocktail";
}

const RecipeChatBot: React.FC<RecipeChatBotProps> = ({ type = "food" }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    { role: "bot", text: "안녕하세요! 재료를 말씀해주시면 레시피를 추천해드릴게요 😊\n예) '계란이랑 두부로 만들 수 있는거 뭐야?'" },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || loading) return;

    setMessages((prev) => [...prev, { role: "user", text: trimmed }]);
    setInput("");
    setLoading(true);

    try {
      const res = await ChatApi.chatRecipe(trimmed, type);
      setMessages((prev) => [
        ...prev,
        {
          role: "bot",
          text: res.message,
          ingredients: res.ingredients,
          recipes: res.recipes,
        },
      ]);
    } catch {
      setMessages((prev) => [...prev, { role: "bot", text: "오류가 발생했어요. 다시 시도해주세요." }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") handleSend();
  };

  return (
    <>
      {/* 플로팅 버튼 */}
      <button
        onClick={() => setIsOpen((prev) => !prev)}
        className="fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full bg-warmOrange text-white shadow-lg flex items-center justify-center text-2xl hover:bg-orange-500 transition"
        title="레시피 챗봇"
      >
        🍳
      </button>

      {/* 챗봇 창 */}
      {isOpen && (
        <div className="fixed bottom-24 right-6 z-50 w-80 h-[480px] flex flex-col rounded-2xl shadow-2xl bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
          {/* 헤더 */}
          <div className="flex items-center justify-between px-4 py-3 bg-warmOrange rounded-t-2xl">
            <span className="text-white font-semibold text-sm">🍳 레시피 추천 챗봇</span>
            <button onClick={() => setIsOpen(false)} className="text-white text-lg leading-none">✕</button>
          </div>

          {/* 메시지 목록 */}
          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            {messages.map((msg, idx) => (
              <div key={idx} className={`flex ${msg.role === "user" ? "justify-end" : "justify-start"}`}>
                <div className={`max-w-[85%] ${msg.role === "user" ? "bg-warmOrange text-white" : "bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-100"} rounded-2xl px-3 py-2 text-sm`}>
                  <p className="whitespace-pre-line">{msg.text}</p>

                  {/* 추출된 재료 태그 */}
                  {msg.ingredients && msg.ingredients.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {msg.ingredients.map((ing) => (
                        <span key={ing} className="bg-orange-100 dark:bg-orange-900 text-orange-700 dark:text-orange-200 text-xs px-2 py-0.5 rounded-full">
                          {ing}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* 레시피 카드 목록 */}
                  {msg.recipes && msg.recipes.length > 0 && (
                    <div className="mt-2 space-y-2">
                      {msg.recipes.slice(0, 5).map((recipe) => (
                        <div key={recipe.id} className="flex items-center gap-2 bg-white dark:bg-gray-600 rounded-lg p-2">
                          {recipe.image && (
                            <img src={recipe.image} alt={recipe.name} className="w-10 h-10 rounded object-cover flex-shrink-0" />
                          )}
                          <div className="min-w-0">
                            <p className="text-xs font-semibold truncate text-gray-800 dark:text-gray-100">{recipe.name}</p>
                            {recipe.category && (
                              <p className="text-xs text-gray-400 dark:text-gray-300 truncate">{recipe.category}</p>
                            )}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex justify-start">
                <div className="bg-gray-100 dark:bg-gray-700 rounded-2xl px-4 py-2 text-sm text-gray-500 dark:text-gray-300">
                  추천 중...
                </div>
              </div>
            )}
            <div ref={bottomRef} />
          </div>

          {/* 입력창 */}
          <div className="flex items-center gap-2 px-3 py-2 border-t border-gray-200 dark:border-gray-700">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="재료를 입력해보세요..."
              className="flex-1 text-sm px-3 py-2 rounded-full border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 text-gray-800 dark:text-gray-100 focus:outline-none"
            />
            <button
              onClick={handleSend}
              disabled={loading}
              className="w-9 h-9 flex items-center justify-center rounded-full bg-warmOrange text-white hover:bg-orange-500 disabled:opacity-50 transition flex-shrink-0"
            >
              ➤
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default RecipeChatBot;
