import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '../shared/components/ProtectedRoute'
import { AppLayout } from '../shared/layouts/AppLayout'
import { RoleGate } from '../shared/components/RoleGate'

import { FeedPage } from '../pages/FeedPage'
import { SearchPage } from '../pages/SearchPage'
import { ProfilePage } from '../pages/ProfilePage'
import { AuthoringPage } from '../pages/AuthoringPage'
import { PostDetailPage } from '../pages/PostDetailPage'
import LoginPage from '../pages/LoginPage'
import RegisterPage from '../pages/RegisterPage'
import NotFoundPage from '../pages/NotFoundPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/feed" replace />,
      },
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
