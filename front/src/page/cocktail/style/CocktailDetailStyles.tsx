const cocktailDetailStyles = {
    container: { maxWidth: 1200, margin: 'auto', padding: 3 },
    cardMedia: { 
        width: "100%",
        height: "auto", 
        maxHeight: "600px", 
        objectFit: "contain", 
    },
    cardContent: { padding: 4 },
    title: { fontWeight: 'bold', marginBottom: 5, fontSize: '2.5rem' },
    gridContainer: { marginBottom: 5 },
    profileBox: { display: "flex", justifyContent: "flex-start", marginBottom: 4 },
    sectionTitle: { fontWeight: 'bold', marginTop: 5, marginBottom: 3, fontSize: '1.8rem' },
    divider: { marginBottom: 3, borderColor: '#6a4e23' },
    ingredientGrid: { marginBottom: 5 },
    garnishGrid: { marginBottom: 4 },
    likeReportBox: { marginTop: 5, marginBottom: 5 },
    commentBox: { marginTop: 6 },
    typography: { fontSize: '1.2rem' },
    
    gridItem: { xs: 12, sm: 6, md: 4 },  // 반복되는 그리드 스타일
    gridFullWidth: { xs: 12 },  // 전체 너비
    garnishGridItem: { xs: 12, md: 6 }, // 가니시 스타일
    gridMarginTop: { xs: 12, marginTop: 2 },
};

export default cocktailDetailStyles;
