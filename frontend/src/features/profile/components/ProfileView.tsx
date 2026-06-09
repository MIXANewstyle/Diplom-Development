import { formatDate } from '../../../shared/lib/format';
import type { MyProfile } from '../types';
import { GENDER_OPTIONS } from '../types';

interface ProfileViewProps {
  profile: MyProfile;
  onEdit: () => void;
}

export function ProfileView({ profile, onEdit }: ProfileViewProps) {
  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6 space-y-4">
      <div className="flex justify-between items-start">
        <h2 className="text-xl font-bold">Профиль</h2>
        <button 
          onClick={onEdit}
          className="text-blue-600 hover:text-blue-800 text-sm font-medium"
        >
          Редактировать
        </button>
      </div>

      {profile.avatarUrl && (
        <div>
          <img 
            src={profile.avatarUrl} 
            alt="Аватар" 
            className="w-24 h-24 rounded-full object-cover border border-gray-200" 
          />
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div>
          <div className="text-sm text-gray-500">Email</div>
          <div>{profile.email}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Роль</div>
          <div>{profile.role}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Имя пользователя (username)</div>
          <div>{profile.username}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Полное имя</div>
          <div>{profile.fullName}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Дата рождения</div>
          <div>{profile.birthDate ? formatDate(profile.birthDate) : 'Не указана'}</div>
        </div>
        <div>
          <div className="text-sm text-gray-500">Пол</div>
          <div>{profile.genderId ? GENDER_OPTIONS[profile.genderId] : 'Не указан'}</div>
        </div>
      </div>

      <div>
        <div className="text-sm text-gray-500">Контактная информация</div>
        <div>{profile.contactInfo || 'Не указана'}</div>
      </div>

      <div>
        <div className="text-sm text-gray-500">О себе (bio)</div>
        <div className="whitespace-pre-wrap">{profile.bio || 'Не указано'}</div>
      </div>
    </div>
  );
}
