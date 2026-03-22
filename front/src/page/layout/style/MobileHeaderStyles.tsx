import styled from "styled-components";

// 모바일 메뉴 (햄버거 클릭 시 표시)
export const MobileMenu = styled.div<{ isMenuOpen: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: #fff;
  border: 1px solid #6a4e23;
  border-top: none;
  width: 45%;
  position: fixed;
  top: 72px;
  right: 0;
  z-index: 50;
  padding: 2rem 0;
  box-shadow: -2px 4px 8px rgba(0, 0, 0, 0.2);
  gap: 2rem;
  transform: translateX(120%); /* 처음엔 화면 밖 */
  transition: transform 0.3s ease-in-out; /* 애니메이션 */

  ${({ isMenuOpen }) => isMenuOpen && `transform: translateX(0);`}
`;

