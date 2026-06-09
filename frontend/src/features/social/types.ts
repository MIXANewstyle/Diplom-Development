export interface FollowedAuthor {
  authorId: string;
  followedAt: string;
}

export interface UserBrief {
  id: string;
  username: string;
  avatarUrl: string | null;
}

export interface MyFriends {
  friends: UserBrief[];
  incomingRequests: UserBrief[];
  outgoingRequests: UserBrief[];
}
