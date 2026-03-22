const recipeDetailStyles = {
    container: { maxWidth: 1200, margin: 'auto', padding: 3 },
    cardMedia: { borderRadius: 2, height: 600, width: '100%', objectFit: 'cover' },
    cardContent: { padding: 4 },
    title: { fontWeight: 'bold', marginBottom: 5, fontSize: '2.5rem' },
    gridContainer: { marginBottom: 5 },
    profileBox: { display: "flex", justifyContent: "flex-start", marginBottom: 4 },
    descriptionBox: { marginBottom: 5 },
    sectionTitle: { fontWeight: 'bold', marginTop: 5, marginBottom: 3, fontSize: '1.8rem' },
    divider: { marginBottom: 3, borderColor: '#6a4e23' },
    ingredientGrid: { marginBottom: 5 },
    instructionGrid: { marginBottom: 6 },
    stepTitle: { fontWeight: 'bold', fontSize: '1.5rem', marginBottom: 2 },
    likeReportBox: { marginTop: 5, marginBottom: 5 },
    commentBox: { marginTop: 6 },
    instructionImage: {
        borderRadius: 2,
        width: '100%',
        maxWidth: 400,
        height: 300,
        objectFit: 'cover',
    },
    typography: { fontSize: "1.2rem" },
};

export default recipeDetailStyles;
