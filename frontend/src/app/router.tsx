import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '../shared/components/ProtectedRoute'
import { AppLayout } from '../shared/layouts/AppLayout'
import { RoleGate } from '../shared/components/RoleGate'

import { LandingPage } from '../pages/LandingPage'
import { FeedPage } from '../pages/FeedPage'
import { SearchPage } from '../pages/SearchPage'
import { ProfilePage } from '../pages/ProfilePage'
import { BillingPage } from '../pages/BillingPage'
import { BillingPaymentPage } from '../pages/BillingPaymentPage'
import { AuthoringPage } from '../pages/AuthoringPage'
import { AuthorPage } from '../pages/AuthorPage'
import { FriendsPage } from '../pages/FriendsPage'
import { PostDetailPage } from '../pages/PostDetailPage'
import { ChatPage } from '../pages/ChatPage'
import { RoomPage } from '../pages/RoomPage'
import { AdminLayout } from '../pages/admin/AdminLayout'
import { AdminUsersPage } from '../pages/admin/AdminUsersPage'
import { AdminTagsPage } from '../pages/admin/AdminTagsPage'
import { AdminBillingPage } from '../pages/admin/AdminBillingPage'
import LoginPage from '../pages/LoginPage'
import RegisterPage from '../pages/RegisterPage'
import NotFoundPage from '../pages/NotFoundPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <LandingPage />,
  },
  {
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        path: 'feed',
        element: <FeedPage />,
      },
      {
        path: 'search',
        element: <SearchPage />,
      },
      {
        path: 'me',
        element: <ProfilePage />,
      },
      {
        path: 'subscription',
        element: <BillingPage />,
      },
      {
        path: 'billing/payment/:transactionId',
        element: <BillingPaymentPage />,
      },
      {
        path: 'friends',
        element: <FriendsPage />,
      },
      {
        path: 'authors/:authorId',
        element: <AuthorPage />,
      },
      {
        path: 'authoring',
        element: (
          <RoleGate allowed={['AUTHOR', 'ADMIN']}>
            <AuthoringPage />
          </RoleGate>
        ),
      },
      {
        path: 'posts/:id',
        element: <PostDetailPage />,
      },
      {
        path: 'chat',
        element: <ChatPage />,
      },
      {
        path: 'chat/:roomId',
        element: <RoomPage />,
      },
      {
        path: 'admin',
        element: (
          <RoleGate allowed={['ADMIN']}>
            <AdminLayout />
          </RoleGate>
        ),
        children: [
          { index: true, element: <Navigate to="users" replace /> },
          { path: 'users', element: <AdminUsersPage /> },
          { path: 'tags', element: <AdminTagsPage /> },
          { path: 'billing', element: <AdminBillingPage /> },
        ],
      },
    ],
  },
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/register',
    element: <RegisterPage />,
  },
  {
    path: '*',
    element: <NotFoundPage />,
  },
])
