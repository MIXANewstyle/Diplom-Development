import { useState } from 'react';
import { useLocation, useParams, useNavigate } from 'react-router-dom';
import { useConfirmStubPayment, useMySubscription } from '../features/billing/hooks';
import { refreshSessionUntilUpgraded } from '../shared/lib/refreshSession';
import { getErrorMessage } from '../shared/lib/errors';
import { ErrorText } from '../shared/components/ErrorText';
import { queryClient } from '../shared/api/queryClient';

interface LocationState {
  planCode?: string;
  amount?: number;
  currency?: string;
}

export function BillingPaymentPage() {
  const { transactionId } = useParams<{ transactionId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as LocationState | null;

  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  
  const { mutateAsync: confirmStub, isPending: confirming } = useConfirmStubPayment();
  const { refetch } = useMySubscription();

  const handlePay = async () => {
    if (!transactionId) return;
    setErrorMsg('');
    try {
      await confirmStub(transactionId);
      setSuccessMsg('Оплата успешна! Активируем подписку...');
      
      // Poll until subscription is ACTIVE
      let isActive = false;
      for (let i = 0; i < 3; i++) {
        const { data } = await refetch();
        if (data && data.status === 'ACTIVE') {
          isActive = true;
          break;
        }
        await new Promise(r => setTimeout(r, 1500));
      }

      if (isActive) {
        setSuccessMsg('Подписка активна! Обновляем сессию...');
        await refreshSessionUntilUpgraded('BASIC');
      }

      // Navigate to feed regardless, the user is likely upgraded
      queryClient.invalidateQueries({ queryKey: ['billing'] });
      navigate('/feed');
    } catch (err) {
      setErrorMsg(getErrorMessage(err));
    }
  };

  const handleCancel = () => {
    navigate('/subscription');
  };

  const isBusy = confirming || !!successMsg;

  return (
    <div className="max-w-md mx-auto mt-10 bg-white border border-gray-200 rounded-lg p-6 shadow-sm">
      <h1 className="text-2xl font-bold mb-4">Оплата подписки</h1>
      
      <div className="bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-6">
        <p className="text-sm text-yellow-800">
          Это тестовая страница оплаты. В реальном приложении здесь был бы переход на страницу платежного провайдера.
        </p>
      </div>

      <div className="mb-6 space-y-2">
        <div className="flex justify-between">
          <span className="text-gray-600">ID Транзакции:</span>
          <span className="font-mono text-sm">{transactionId}</span>
        </div>
        {state?.planCode && (
          <div className="flex justify-between">
            <span className="text-gray-600">Тариф:</span>
            <span className="font-medium">{state.planCode}</span>
          </div>
        )}
        {state?.amount !== undefined && state?.currency && (
          <div className="flex justify-between">
            <span className="text-gray-600">К оплате:</span>
            <span className="font-bold">{state.amount} {state.currency}</span>
          </div>
        )}
      </div>

      <ErrorText error={errorMsg} className="bg-red-50 p-3 rounded mb-4 mt-0" />
      {successMsg && (
        <div className="bg-green-50 text-green-700 p-3 rounded text-sm mb-4">
          {successMsg}
        </div>
      )}

      <div className="flex gap-3 justify-end">
        <button
          onClick={handleCancel}
          disabled={isBusy}
          className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded border border-gray-300 disabled:opacity-50"
        >
          Отмена
        </button>
        <button
          onClick={handlePay}
          disabled={isBusy || !transactionId}
          className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
        >
          {confirming ? 'Оплата...' : 'Оплатить (тестовая оплата)'}
        </button>
      </div>
    </div>
  );
}
