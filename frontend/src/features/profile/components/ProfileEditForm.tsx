import { useState } from 'react';
import type { MyProfile, GenderId } from '../types';
import { GENDER_OPTIONS } from '../types';
import { useUpdateProfile } from '../hooks/useUpdateProfile';
import axios from 'axios';

interface ProfileEditFormProps {
  profile: MyProfile;
  onCancel: () => void;
  onSaved: () => void;
}

export function ProfileEditForm({ profile, onCancel, onSaved }: ProfileEditFormProps) {
  const [fullName, setFullName] = useState(profile.fullName);
  const [username, setUsername] = useState(profile.username);
  const [bio, setBio] = useState(profile.bio || '');
  const [avatarUrl, setAvatarUrl] = useState(profile.avatarUrl || '');
  const [contactInfo, setContactInfo] = useState(profile.contactInfo || '');
  const [birthDate, setBirthDate] = useState(profile.birthDate || '');
  const [genderId, setGenderId] = useState<string>(profile.genderId ? String(profile.genderId) : '');
  
  const [errorMsg, setErrorMsg] = useState('');
  const { mutateAsync, isPending } = useUpdateProfile();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');

    if (username.length < 3) {
      setErrorMsg('Имя пользователя должно быть не менее 3 символов');
      return;
    }
    
    if (fullName.trim().length === 0) {
      setErrorMsg('Полное имя не может быть пустым');
      return;
    }

    try {
      await mutateAsync({
        fullName: fullName.trim(),
        username: username.trim(),
        bio: bio.trim() || null,
        avatarUrl: avatarUrl.trim() || null,
        contactInfo: contactInfo.trim() || null,
        birthDate: birthDate || null,
        genderId: genderId ? (parseInt(genderId, 10) as GenderId) : null,
      });
      onSaved();
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        if (err.response?.status === 409) {
          setErrorMsg('Имя пользователя уже занято');
        } else {
          setErrorMsg(err.response?.data?.message || 'Ошибка сохранения');
        }
      } else {
        setErrorMsg('Неизвестная ошибка');
      }
    }
  };

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6">
      <h2 className="text-xl font-bold mb-4">Редактирование профиля</h2>
      
      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm mb-4">
          {errorMsg}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Имя пользователя (от 3 символов)</label>
          <input 
            type="text" 
            value={username} 
            onChange={e => setUsername(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2"
            required
            minLength={3}
            maxLength={100}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Полное имя</label>
          <input 
            type="text" 
            value={fullName} 
            onChange={e => setFullName(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2"
            required
            maxLength={255}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">URL аватара</label>
          <input 
            type="url" 
            value={avatarUrl} 
            onChange={e => setAvatarUrl(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2"
            maxLength={2048}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Дата рождения</label>
            <input 
              type="date" 
              value={birthDate} 
              onChange={e => setBirthDate(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Пол</label>
            <select 
              value={genderId} 
              onChange={e => setGenderId(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2"
            >
              <option value="">Не указано</option>
              {Object.entries(GENDER_OPTIONS).map(([id, label]) => (
                <option key={id} value={id}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Контактная информация</label>
          <input 
            type="text" 
            value={contactInfo} 
            onChange={e => setContactInfo(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2"
            maxLength={1000}
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">О себе (bio)</label>
          <textarea 
            value={bio} 
            onChange={e => setBio(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2 min-h-[100px]"
            maxLength={5000}
          />
        </div>

        <div className="flex gap-2 justify-end pt-4">
          <button 
            type="button" 
            onClick={onCancel}
            disabled={isPending}
            className="px-4 py-2 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
          >
            Отмена
          </button>
          <button 
            type="submit" 
            disabled={isPending}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            Сохранить
          </button>
        </div>
      </form>
    </div>
  );
}
