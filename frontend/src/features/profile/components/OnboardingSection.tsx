import { useState, useEffect } from 'react';
import type { MyProfile, PsychProfile } from '../types';
import { parsePsychProfile, serializePsychProfile, psychProfileLength } from '../lib/psychProfile';
import { useUpdateProfile } from '../hooks/useUpdateProfile';
import { getErrorMessage } from '../../../shared/lib/errors';
import { ErrorText } from '../../../shared/components/ErrorText';

interface OnboardingSectionProps {
  profile: MyProfile;
}

export function OnboardingSection({ profile }: OnboardingSectionProps) {
  const [form, setForm] = useState<PsychProfile>({});
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  
  const { mutateAsync, isPending } = useUpdateProfile();

  useEffect(() => {
    setForm(parsePsychProfile(profile.psychProfile));
  }, [profile.psychProfile]);

  const currentLen = psychProfileLength(form);
  const isOverLimit = currentLen > 1000;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setSuccessMsg('');

    if (isOverLimit) {
      return;
    }

    try {
      await mutateAsync({
        psychProfile: serializePsychProfile(form)
      });
      setSuccessMsg('Анкета сохранена');
      setTimeout(() => setSuccessMsg(''), 3000);
    } catch (err: unknown) {
      setErrorMsg(getErrorMessage(err));
    }
  };

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6">
      <h2 className="text-xl font-bold mb-2">Анкета (контекст для ИИ)</h2>
      <p className="text-sm text-gray-500 mb-4">
        Заполните эти поля, чтобы ИИ-терапевт лучше понимал вашу ситуацию. Это необязательно.
      </p>

      <ErrorText error={errorMsg} className="bg-red-50 p-3 rounded mb-4 mt-0" />
      {successMsg && (
        <div className="bg-green-50 text-green-700 p-3 rounded text-sm mb-4">
          {successMsg}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">О себе</label>
          <textarea 
            value={form.about_self || ''} 
            onChange={e => setForm(prev => ({ ...prev, about_self: e.target.value }))}
            placeholder="Расскажите о себе в свободной форме"
            className="w-full border border-gray-300 rounded px-3 py-2 min-h-[100px]"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Что привело</label>
          <textarea 
            value={form.reason || ''} 
            onChange={e => setForm(prev => ({ ...prev, reason: e.target.value }))}
            className="w-full border border-gray-300 rounded px-3 py-2 min-h-[80px]"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Над чем хотите поработать</label>
          <textarea 
            value={form.goals || ''} 
            onChange={e => setForm(prev => ({ ...prev, goals: e.target.value }))}
            className="w-full border border-gray-300 rounded px-3 py-2 min-h-[80px]"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Опыт терапии</label>
          <select 
            value={form.prior_experience || ''} 
            onChange={e => setForm(prev => ({ ...prev, prior_experience: e.target.value as any }))}
            className="w-full border border-gray-300 rounded px-3 py-2"
          >
            <option value="">Не указано</option>
            <option value="none">Нет</option>
            <option value="some">Немного</option>
            <option value="extensive">Большой</option>
          </select>
        </div>

        <div className="flex items-center justify-between pt-4">
          <div className={`text-sm ${isOverLimit ? 'text-red-600 font-bold' : 'text-gray-500'}`}>
            Символов: {currentLen} / 1000
          </div>
          <button 
            type="submit" 
            disabled={isPending || isOverLimit}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Сохранить анкету
          </button>
        </div>
        {isOverLimit && (
          <div className="text-red-600 text-sm mt-1 text-right">
            Превышен лимит символов (1000). Пожалуйста, сократите текст.
          </div>
        )}
      </form>
    </div>
  );
}
