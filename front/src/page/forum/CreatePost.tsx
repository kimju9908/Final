import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useDispatch, useSelector } from "react-redux";
import ForumApi from "../../api/ForumApi";
import { toast } from "react-toastify";
import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Bold from "@tiptap/extension-bold";
import Italic from "@tiptap/extension-italic";
import Underline from "@tiptap/extension-underline";
import Link from "@tiptap/extension-link";
import TextStyle from "@tiptap/extension-text-style";
import Blockquote from "@tiptap/extension-blockquote";
import ConfirmationModal from "./ConfirmationModal";
import { openModal } from "../../context/redux/ModalReducer";

// 인터페이스 확장 (sticky 필드 추가)
interface FormDataType {
  title: string;
  categoryId: string;
  content: string;
  contentJSON: string;
  sticky?: boolean;
}

interface Category {
  id: string;
  title: string;
}

const stripHTML = (html: string): string => {
  const tempDiv = document.createElement("div");
  tempDiv.innerHTML = html;
  return tempDiv.textContent || tempDiv.innerText || "";
};

const convertHtmlToJson = (html: string) => ({
  type: "doc",
  content: [
    {
      type: "paragraph",
      content: [{ type: "text", text: stripHTML(html) }],
    },
  ],
});

const CreatePost: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useDispatch();

  // Redux 스토어에서 사용자 정보 가져오기
  const user = useSelector((state: any) => state.user);
  // 관리자 여부 변수 (관리자라면 true)
  const isAdmin = user && user.admin;
  // 프리미엄 회원 여부 변수 (사용자 정보에 premium 플래그가 있다고 가정)
  const isPremium = user && user.premium;

  const [formData, setFormData] = useState<FormDataType>({
    title: "",
    categoryId: "",
    content: "",
    contentJSON: "",
    sticky: false,
  });
  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState<boolean>(false);
  const [isModalOpen, setIsModalOpen] = useState<boolean>(false);

  const editor = useEditor({
    extensions: [
      StarterKit,
      Bold,
      Italic,
      Underline,
      Link.configure({ openOnClick: false }),
      TextStyle,
      Blockquote,
    ],
    content: "",
    onUpdate: ({ editor }) => {
      const html = editor.getHTML();
      const json = editor.getJSON();
      setFormData((prev) => ({
        ...prev,
        content: html,
        contentJSON:
          JSON.stringify(json) || JSON.stringify({ type: "doc", content: [] }),
      }));
    },
  });

  // 만약 사용자 정보가 없으면 로그인 모달 열기
  useEffect(() => {
    if (!user || !user.id) {
      toast.error("로그인이 필요합니다.");
      dispatch(openModal("login"));
    }
  }, [user, dispatch]);

  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const categoryData = await ForumApi.fetchCategories();
        setCategories((Array.isArray(categoryData) ? categoryData : []) as Category[]);
      } catch (error) {
        console.error("카테고리 로딩 오류:", error);
        toast.error("카테고리 정보를 불러오는 중 오류가 발생했습니다.");
        setCategories([]);
      }
    };
    fetchCategories();
  }, []);

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setUploading(true);

    const currentJSON = editor
      ? JSON.stringify(editor.getJSON())
      : formData.contentJSON || JSON.stringify({ type: "doc", content: [] });

    // Redux의 사용자 정보와 결합하여 게시글 데이터 구성
    const postData = {
      ...formData,
      memberId: user ? user.id.toString() : "",
      fileUrls: [],
      contentJSON: currentJSON,
    };

    try {
      const response = await ForumApi.createPostAndFetch(postData);
      toast.success("게시글이 성공적으로 생성되었습니다!");
      navigate(`/forum/post/${response.id}`);
    } catch (error) {
      console.error("게시글 생성 중 오류:", error);
      toast.error("게시글 생성에 실패했습니다.");
    } finally {
      setUploading(false);
    }
  };

  const openLinkModal = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    setIsModalOpen(true);
  };

  const handleAddLink = (url: string) => {
    if (!url) return;
    editor
      ?.chain()
      .focus()
      .extendMarkRange("link")
      .setLink({ href: url })
      .run();
    setIsModalOpen(false);
  };

  return (
    <div className="bg-gray-100 w-full md:max-w-[1200px] mx-auto my-10 p-6 rounded-lg shadow-md">
      <h2 className="text-3xl font-bold text-center mb-6">게시글 생성</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {/* 제목 입력 */}
        <div className="flex flex-col gap-1">
          <label htmlFor="title" className="font-semibold text-gray-700">
            제목
          </label>
          <input
            type="text"
            id="title"
            name="title"
            value={formData.title}
            onChange={handleChange}
            required
            className="p-2 border border-gray-300 rounded"
          />
        </div>
        {/* 카테고리 선택 */}
        <div className="flex flex-col gap-1">
          <label htmlFor="categoryId" className="font-semibold text-gray-700">
            카테고리
          </label>
          <select
            id="categoryId"
            name="categoryId"
            value={formData.categoryId}
            onChange={handleChange}
            required
            className="p-2 border border-gray-300 rounded"
          >
            <option value="">카테고리를 선택하세요</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.title}
              </option>
            ))}
          </select>
        </div>
        {/* 에디터 */}
        <div className="flex flex-col gap-2">
          <label className="font-semibold text-gray-700">내용</label>
          <div className="flex gap-2 mb-2">
            <button
              type="button"
              onClick={() => editor?.chain().focus().toggleBold().run()}
              className="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              B
            </button>
            <button
              type="button"
              onClick={() => editor?.chain().focus().toggleItalic().run()}
              className="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              I
            </button>
            <button
              type="button"
              onClick={() => editor?.chain().focus().toggleUnderline().run()}
              className="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              U
            </button>
            <button
              type="button"
              onClick={openLinkModal}
              className="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              Link
            </button>
            <button
              type="button"
              onClick={() => editor?.chain().focus().unsetLink().run()}
              className="px-3 py-1 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              Remove Link
            </button>
          </div>
          <EditorContent
            editor={editor}
            className="border border-gray-300 rounded p-2 min-h-[200px] bg-white"
          />
        </div>
        {/* 관리자 및 프리미엄 회원용 고정 게시글 옵션 */}
        {/* 
            고정 게시글 옵션은 관리자인 경우와 프리미엄 회원인 경우에 모두 표시됩니다.
            (user.premium 값이 true이면 프리미엄 회원으로 간주)
         */}
        {(isAdmin || isPremium) && (
          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="sticky"
              name="sticky"
              checked={formData.sticky || false}
              onChange={(e) =>
                setFormData((prev) => ({ ...prev, sticky: e.target.checked }))
              }
            />
            <label htmlFor="sticky" className="font-semibold text-gray-700">
              고정 게시글로 생성
            </label>
          </div>
        )}
        {/* 파일 첨부 (선택 사항) */}
        <div className="flex flex-col gap-1">
          <label htmlFor="file" className="font-semibold text-gray-700">
            파일 첨부 (선택 사항)
          </label>
          <input
            type="file"
            id="file"
            onChange={handleFileChange}
            className="p-2 border border-gray-300 rounded"
          />
        </div>
        {/* 제출 버튼 */}
        <button
          type="submit"
          disabled={uploading}
          className={`py-2 rounded font-semibold ${
            uploading ? "bg-gray-400" : "bg-blue-500 hover:bg-blue-600"
          } text-white`}
        >
          {uploading ? "업로드 중..." : "게시글 생성"}
        </button>
      </form>
      <ConfirmationModal
        isOpen={isModalOpen}
        type="addLink"
        content={""}
        message="추가할 링크를 입력하세요:"
        onConfirm={handleAddLink}
        onCancel={() => setIsModalOpen(false)}
      />
    </div>
  );
};

export default CreatePost;
