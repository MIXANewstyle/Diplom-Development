import { useState } from 'react';
import { getErrorMessage } from '../shared/lib/errors';
import { useAuthStore } from '../shared/stores/authStore';
import { useMyProfile } from '../features/profile/hooks/useMyProfile';
import { ProfileView } from '../features/profile/components/ProfileView';
import { ProfileEditForm } from '../features/profile/components/ProfileEditForm';
import { OnboardingSection } from '../features/profile/components/OnboardingSection';

export function ProfilePage() {
  const { user } = useAuthStore();
  const { data: profile, isLoading, error } = useMyProfile();
  const [isEditing, setIsEditing] = useState(false);

  if (!user) return null;

  if (isLoading) {
    return <div className="p-4 text-gray-500">Загрузка профиля...</div>;
  }

  if (error || !profile) {
    return <div className="p-4 text-red-600">{error ? getErrorMessage(error) : 'Ошибка загрузки профиля'}</div>;
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <h1 className="text-2xl font-bold">Мой профиль</h1>
      
      {isEditing ? (
        <ProfileEditForm 
          profile={profile} 
          onCancel={() => setIsEditing(false)} 
          onSaved={() => setIsEditing(false)} 
        />
      ) : (
        <ProfileView 
          profile={profile} 
          onEdit={() => setIsEditing(true)} 
        />
      )}

      <OnboardingSection profile={profile} />
    </div>
  );
}
