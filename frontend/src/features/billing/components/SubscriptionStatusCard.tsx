import { useMySubscription } from '../hooks';
import { formatDate } from '../../../shared/lib/format';
import { useAuthStore } from '../../../shared/stores/authStore';
import { queryClient } from '../../../shared/api/queryClient';
import { useNavigate } from 'react-router-dom';

export function SubscriptionStatusCard() {
  const { data: subscription, isLoading } = useMySubscription();
  const { clearAuth } = useAuthStore();
  const navigate = useNavigate();

  const handleRelogin = () => {
    clearAuth();
    queryClient.clear();
    navigate('/login');
  };

  if (isLoading) {
    return <div className="p-4 text-gray-500">Загрузка статуса подписки...</div>;
  }

  // Treat 404 or missing as "no active subscription"
  // Note: Backend might return 404 or empty response if there is no subscription. We handle it defensively.
  const hasActiveSub = subscription && subscription.status === 'ACTIVE';

  if (!subscription || !hasActiveSub) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-6">
        <h2 className="text-xl font-bold mb-2">Статус подписки</h2>
        <p className="text-gray-600">Нет активной подписки</p>
      </div>
    );
  }

  return (
    <div className="bg-green-50 border border-green-200 rounded-lg p-6 space-y-4">
      <div className="flex justify-between items-start">
        <h2 className="text-xl font-bold text-green-900">Текущая подписка</h2>
        <span className="bg-green-200 text-green-800 text-xs font-bold px-2 py-1 rounded">
          {subscription.tier}
        </span>
      </div>
      
      <div className="space-y-1">
        <div className="text-sm text-green-800">
          <span className="font-semibold">Статус:</span> {subscription.status}
        </div>
        <div className="text-sm text-green-800">
          <span className="font-semibold">Действует до:</span> {formatDate(subscription.expiresAt)}
        </div>
      </div>

      <div className="bg-white rounded p-4 text-sm text-gray-700 shadow-sm">
        <p className="mb-2">
          Подписка оформлена и активна. Чтобы открыть доступ к контенту, войдите в аккаунт заново.
        </p>
        <button 
          onClick={handleRelogin}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          Выйти и войти заново
        </button>
      </div>
    </div>
  );
}
