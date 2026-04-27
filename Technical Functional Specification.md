### Functional Specification: User Service (user\_service)

**1. Authentication & Account Management** This module manages the core identity of the user, strictly separated from public profile data to ensure security.

- User Registration: The service must accept email and password, verifying the uniqueness of the email address.
- Security: Passwords must be hashed using industry-standard algorithms (e.g., Bcrypt/Argon2) before database persistence.
- Account Initialization: Upon successful registration, the service must assign a default role\_id (e.g., FREE) and status\_id (e.g., ACTIVE).
- Audit & Versioning: The service must automatically generate the created\_at timestamp and initialize the version field (set to 0) for optimistic locking purposes.
- Profile Linkage: Simultaneously with account creation, the service must transactionally create an empty record in the profiles table (1:1 relationship).

**2. Profile & Identity Management** This module manages both public-facing and internal user metadata.

- Profile Updates: Users must be able to update their public display name (username), full name, biography, and contact information.
- Media Integration: The service must handle links to S3-stored images for the avatar\_url.
- Psychological Context: The service must support saving and updating unstructured psychological data in JSONB format for AI-mediated interactions.
- Change Tracking: Any modification to the profile must trigger an update to the updated\_at field to maintain a clear audit trail.

**3. Social Graph & Networking** This module governs the relationships and interaction boundaries between users.

- Friendship Requests: The service must facilitate friendship requests by creating records in the friendships table with a PENDING status, capturing both requester\_id and addressee\_id.
- Status Management: Users can update the friendship status to ACCEPTED or DECLINED.
- Interaction History: The service must strictly separate the initial request time (created\_at) from the last status modification time (updated\_at).
- Author Following: The service must implement one-way subscriptions through the author\_follows table, allowing users to follow specialists (users with the AUTHOR role).

**4. RBAC & Administrative Control** This module provides the necessary hooks for system governance and access control.

- Authorization Support: The service must provide current user role data (GUEST, FREE, BASIC, AUTHOR, ADMIN) to support JWT token generation and method-level security.
- Moderation API: The service must expose internal API endpoints specifically for the Admin Service (BFF) to allow authorized moderators to manually override a user's role\_id and status\_id (e.g., for verification or banning).

5. Distributed Event Synchronization (Transactional Outbox)

To ensure data consistency across the microservice architecture without using distributed transactions (2PC), the User Service implements the **Transactional Outbox** pattern. This approach guarantees "at-least-once" delivery by persisting event payloads within the same database transaction as the business entity update.

5.1. Database Schema: user\_outbox\_events

The following table is used to buffer events within the user\_schema:

| **Column** | **Type** | **Constraints** | **Description** |
| --- | --- | --- | --- |
| **id** | UUID | PK | Unique event identifier |
| **event\_type** | VARCHAR(100) | NOT NULL | Type of business event (e.g., 'USER\_REGISTERED') |
| **payload** | JSONB | NOT NULL | Event data for downstream consumers |
| **status** | VARCHAR(50) | NOT NULL | Delivery state: 'PENDING' or 'PROCESSED'  Needs B-tree indexing. |
| **created\_at** | TIMESTAMP WITH TIME ZONE | NOT NULL | Event generation timestamp |

5.2. Event Catalog & Inter-Service Orchestration

All events must be recorded atomically with their triggering business logic.

- USER\_REGISTERED
- Trigger: Successful persistence in users and profiles tables during registration.
- Consumers: Content Service, Billing Service.
- Purpose: Initializes user-specific data structures and subscription state in downstream services.
- PROFILE\_CHANGED
- Trigger: Updates to username, full\_name, or avatar\_url in the profiles table.
- Consumers: Content Service.
- Purpose: Synchronizes public identity data to prevent stale information in posts and comments.
- ROLE\_UPDATED
- Trigger: Modification of role\_id via administrative action or automated billing events.
- Consumers: Content Service, Chat Service.
- Purpose: Dynamically adjusts access permissions (e.g., granting AUTHOR privileges).
- ACCOUNT\_MODERATED
- Trigger: Manual or automated update of status\_id to BANNED or DELETED.
- Consumers: All Microservices.
- Purpose: Immediate revocation of session tokens and restriction of system-wide interactions.
- FRIENDSHIP\_ACCEPTED
- Trigger: Transition of a friendships record status from PENDING to ACCEPTED.
- Consumers: Chat Service.
- Purpose: Enables the creation of private or pair-therapy rooms between the involved users.

5.3. Operational Requirements

- Event Polling: A background worker must query the user\_outbox\_events table for 'PENDING' records, publish them to the message broker (e.g., RabbitMQ/Kafka), and mark them as 'PROCESSED'.
- Retention Policy: A scheduled job (cron) must archive or purge 'PROCESSED' events every 24 hours to prevent table bloat and performance degradation.
- Indexing: A B-tree index on the status column is mandatory to optimize polling performance.