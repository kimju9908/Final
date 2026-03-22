export const likeReportButtonStyles = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: 4,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 10,
  },
  countBox: {
    display: 'flex',
    alignItems: 'center',
    marginBottom: 2,
    gap: 5,
  },
  countText: {
    fontSize: 20,
    marginRight: 3,
    fontWeight: 'bold',
  },
  buttonBox: {
    display: 'flex',
    gap: 6,
  },
  likeButton: (isLiked: boolean) => ({
    color: isLiked ? 'red' : 'inherit',
    fontSize: 32,
  }),
  reportButton: (isReported: boolean) => ({
    color: isReported ? 'orange' : 'inherit',
    fontSize: 32,
  }),
  iconStyle: {
    fontSize: 'inherit',
  },
};

