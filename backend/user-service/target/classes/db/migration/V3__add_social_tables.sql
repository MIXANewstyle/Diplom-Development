CREATE TABLE user_schema.friendship_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO user_schema.friendship_statuses (id, name) VALUES
(1, 'PENDING'),
(2, 'ACCEPTED'),
(3, 'DECLINED'),
(4, 'BLOCKED');

CREATE TABLE user_schema.author_follows (
    follower_id UUID REFERENCES user_schema.users(id),
    author_id UUID REFERENCES user_schema.users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (follower_id, author_id)
);

CREATE TABLE user_schema.friendships (
    requester_id UUID REFERENCES user_schema.users(id),
    addressee_id UUID REFERENCES user_schema.users(id),
    status_id INT REFERENCES user_schema.friendship_statuses(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (requester_id, addressee_id)
);
