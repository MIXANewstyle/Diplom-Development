import { useState } from 'react';
import axios from 'axios';
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
    let errorMsg = 'Ошибка загрузки профиля';
    if (axios.isAxiosError(error) && error.response?.data?.message) {
      errorMsg = error.response.data.message;
    } else if (error instanceof Error) {
      errorMsg = error.message;
    }
    return <div className="p-4 text-red-600">{errorMsg}</div>;
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
