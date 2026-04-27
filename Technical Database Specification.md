**Technical Database Specification: AI-Mediated Therapy Platform**

**1. Architectural Guidelines (For AI Agents & Developers)**

- Architecture Pattern: Schema-per-service (PostgreSQL).
- Cross-Service Relations: Strictly implemented via Soft Links (UUIDs). No physical Foreign Keys (FK) are allowed between different schemas.
- Internal Relations: Standard physical Foreign Keys (FK) are used within the bounds of a single schema.
- Dictionaries (Domains): Implemented as lookup tables with INT Primary Keys to optimize indexing and enforce strict domain constraints.
- Distributed Transactions: Saga pattern implemented via the Transactional Outbox pattern. Every schema contains an outbox\_events table.
- TIMESTAMP WITH TIME ZONEs: All time-based fields use TIMESTAMP WITH TIME ZONE (or TIMESTAMP WITH TIME ZONE WITH TIME ZONE).

**2. User Service (user\_schema)**

Manages authentication, profiles, and Role-Based Access Control (RBAC).

**2.1. Dictionaries (Domains)**

| **Table** | **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- | --- |
| user\_roles | id | INT | PK | 1=GUEST, 2=FREE, 3=BASIC, 4=AUTHOR, 5=ADMIN |
| user\_roles | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation of the role |
| user\_statuses | id | INT | PK | e.g., 1=ACTIVE, 2=BANNED, 3=DELETED |
| user\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation of the status |
| genders | id | INT | PK | e.g., 1=MALE, 2=FEMALE, 3=OTHER |
| genders | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation of the gender |
| friendship\_statuses | id | INT | PK | 1=PENDING, 2=ACCEPTED, 3=DECLINED, 4=BLOCKED |
| friendship\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation of the status |

**2.2. Core Tables**

**Table: users**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| email | VARCHAR(255) | UNIQUE, NOT NULL | User's email (Needs B-Tree Index) |
| password\_hash | VARCHAR(255) | NOT NULL | Bcrypt/Argon2 hash |
| role\_id | INT | FK -> user\_roles(id) | User's RBAC role |
| status\_id | INT | FK -> user\_statuses(id) | Account status |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Registration TIMESTAMP WITH TIME ZONE |
| version | INT | DEFAULT 0 NOT NULL | Optimistic locking for Hibernate (@Version) |

**Table: profiles**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| user\_id | UUID | PK, FK -> users(id) | 1:1 relationship with users |
| full\_name | VARCHAR(255) | NOT NULL | Legal or preferred name |
| bio | TEXT | NULLABLE | "About me" for psychologists |
| contact\_info | TEXT | NULLABLE | Contact details for psychologists |
| birth\_date | DATE | NULLABLE | For age calculation |
| gender\_id | INT | FK -> genders(id) | User's gender |
| username | VARCHAR(100) | UNIQUE, NOT NULL | Public display name |
| avatar\_url | TEXT | NULLABLE | Link to storage (S3) |
| psych\_profile | JSONB | NULLABLE | Psychological data for AI context |
| updated\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | update TIMESTAMP WITH TIME ZONE |

**Table: author\_follows**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| follower\_id | UUID | PK, FK -> users(id) | The user who follows |
| author\_id | UUID | PK, FK -> users(id) | The author being followed |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Subscription time |

**Table: friendships**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| requester\_id | UUID | PK, FK -> users(id) | User who initiated the request  Needs B-tree index |
| addressee\_id | UUID | PK, FK -> users(id) | User who received the request Needs B-tree index |
| status\_id | INT | FK -> friendship\_statuses(id) | Current state of friendship |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Request creation time |
| updated\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Last status update time |

**Table: user\_outbox\_events**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Event ID |
| event\_type | VARCHAR(100) | NOT NULL | e.g., 'USER\_CREATED' |
| payload | JSONB | NOT NULL | Event data |
| status | VARCHAR(50) | NOT NULL | 'PENDING' or 'PROCESSED' |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Event generation time |

**3. Content Service (content\_schema)**

Manages posts, tags, comments, and social interactions.

**3.1. Dictionaries (Domains)**

| **Table** | **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- | --- |
| post\_statuses | id | INT | PK | e.g., 1=DRAFT, 2=PUBLISHED, 3=ARCHIVED |
| post\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |

**3.2. Core Tables**

**Table: posts**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| author\_id | UUID | **Soft Link** | Logical link to user\_schema.users(id). Needs Composite B-tree index (author\_id, published\_at DESC**)** |
| cover\_image\_url | TEXT | NULLABLE | Header image link |
| title | VARCHAR(255) | NOT NULL | Post title |
| content | JSONB | NOT NULL | Rich text blocks (Editor.js format)  Needs GIN-index |
| keywords | TEXT[] | NULLABLE | Array of strings for search  Needs GIN-index |
| status\_id | INT | FK -> post\_statuses(id) | Publication state |
| published\_at | TIMESTAMP WITH TIME ZONE | NULLABLE | Used for sorting feed. Covered by Composite Index with author\_id |
| views\_count | INT | DEFAULT 0 | Denormalized views counter |
| upvotes\_count | INT | DEFAULT 0 | Denormalized counter |
| comments\_count | INT | DEFAULT 0 | Denormalized counter |
| updated\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | update TIMESTAMP WITH TIME ZONE |
| version | INT | DEFAULT 0 NOT NULL | Optimistic locking for Hibernate (@Version) |
| search\_vector | TSVECTOR | GENERATED ALWAYS STORED | Full-text search vector (title + content). Needs GIN-index |

**Table: tags** & **post\_tags** (Many-to-Many)

| **Table** | **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- | --- |
| tags | id | UUID | PK | Tag identifier |
| tags | name | VARCHAR(50) | UNIQUE, NOT NULL | Tag text |
| post\_tags | post\_id | UUID | PK, FK -> posts(id) | Composite PK part 1 |
| post\_tags | tag\_id | UUID | PK, FK -> tags(id) | Composite PK part 2 |

**Table: comments** (Adjacency List Pattern)

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| post\_id | UUID | FK -> posts(id) | Associated post  Needs B-tree index for FK constraints |
| author\_id | UUID | **Soft Link** | Logical link to user\_schema.users(id) |
| parent\_id | UUID | FK -> comments(id) | NULL if top-level comment |
| content | TEXT | NOT NULL | Comment body |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Creation time |
| updated\_at | TIMESTAMP WITH TIME ZONE | NULLABLE | Last edit time |

**Table: post\_upvotes**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| post\_id | UUID | PK, FK -> posts(id) | Composite PK part 1 |
| user\_id | UUID | PK, **Soft Link** | Logical link to user\_schema.users(id) |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | TIMESTAMP WITH TIME ZONE of action |

*(Includes content\_outbox\_events mirroring the structure from User Service)*

**4. Chat Service (chat\_schema)**

Manages AI and Couple therapy sessions.

**4.1. Dictionaries (Domains)**

| **Table** | **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- | --- |
| room\_types | id | INT | PK | e.g., 1=SOLO, 2=COUPLE |
| room\_types | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |
| room\_statuses | id | INT | PK | e.g., 1=ACTIVE, 2=ARCHIVED |
| room\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |

**4.2. Core Tables**

**Table: rooms**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| type\_id | INT | FK -> room\_types(id) | Solo or Couple session |
| status\_id | INT | FK -> room\_statuses(id) | Current state |
| active\_turn\_id | UUID | **Soft Link** | Nullable. ID of user whose turn it is. NULL = AI's turn |
| updated\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | update TIMESTAMP WITH TIME ZONE |
| version | INT | DEFAULT 0 NOT NULL | Optimistic locking for Hibernate (@Version) |

**Table: participants**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| room\_id | UUID | PK, FK -> rooms(id) | Composite PK part 1 |
| user\_id | UUID | PK, **Soft Link** | Logical link to user\_schema.users(id)  Needs B-tree index |

**Table: messages**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| room\_id | UUID | FK -> rooms(id) | Chat session ID. Needs B-tree index for FK constraints |
| sender\_id | UUID | **Soft Link** | Link to users. NULL if sent by AI  Needs B-tree index |
| content | TEXT | NOT NULL | Message body |
| ai\_analysis | JSONB | NULLABLE | Hidden NLP analysis vectors/tags |
| is\_locked | BOOLEAN | DEFAULT FALSE | True if turn is passed |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Chronological ordering |

*(Includes chat\_outbox\_events mirroring the structure from User Service)*

**5. Billing Service (billing\_schema)**

Handles subscription state and transaction logs.

**5.1. Dictionaries (Domains)**

| **Table** | **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- | --- |
| sub\_tiers | id | INT | PK | e.g., 1=BASIC, 2=PREMIUM |
| sub\_tiers | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |
| sub\_statuses | id | INT | PK | e.g., 1=ACTIVE, 2=CANCELED, 3=EXPIRED |
| sub\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |
| txn\_statuses | id | INT | PK | 1=PENDING, 2=SUCCESS, 3=FAILED, 4=REFUNDED |
| txn\_statuses | name | VARCHAR(50) | UNIQUE, NOT NULL | String representation |

**5.2. Core Tables**

**Table: subscriptions**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Unique identifier |
| user\_id | UUID | **Soft Link** | Logical link to user\_schema.users(id) |
| tier\_id | INT | FK -> sub\_tiers(id) | Current tier |
| status\_id | INT | FK -> sub\_statuses(id) | Current state |
| expires\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Expiration date |
| version | INT | DEFAULT 0 NOT NULL | Optimistic locking for Hibernate (@Version) |

**Table: transactions**

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| id | UUID | PK | Payment Gateway reference ID |
| user\_id | UUID | **Soft Link** | Logical link to user\_schema.users(id) |
| amount | DECIMAL(10,2) | NOT NULL | Monetary value |
| status\_id | INT | FK -> txn\_statuses(id) | Payment lifecycle state |
| created\_at | TIMESTAMP WITH TIME ZONE | NOT NULL | Transaction TIMESTAMP WITH TIME ZONE |

*(Includes billing\_outbox\_events mirroring the structure from User Service)*

**Specific recommendations for improving the database**

Transactional counter issue: Denormalizing counters (likes, views) in the posts table eliminates heavy COUNT() calls. However, transactional updates to these fields with each like will create hard row-level locks. For a viral post, this will lead to performance degradation. I recommend collecting increments in a cache (e.g., Redis) and flushing them to the database in batches every few minutes.

UUID as a Primary Key: Using the standard UUIDv4 (random) as a primary key leads to severe fragmentation of B-tree indexes during inserts, as the values ​​are not sequential. If possible, use UUIDv7 (it is sorted by generation time) or generate sequential UUIDs on the application/database side.

The outbox\_events table: This table will grow indefinitely. Be sure to set up a background job (cron) to delete or archive successfully processed events so that the table doesn't slow down the database.

Connection Pooling: Microservices tend to open many database connections. Be sure to use a connection pool (e.g., PgBouncer) before PostgreSQL to avoid running out of connections and reduce the overhead of creating them.

Table Partitioning: The messages and transactions (billing) tables will become huge over time. It's worth partitioning them by time (e.g., month or year) using PostgreSQL declarative partitioning. This will significantly speed up read operations by pruning unnecessary partitions (partition pruning).