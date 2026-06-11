import { useState } from 'react';
import { usePlans, useMySubscription, useClaimTrial } from '../hooks';
import type { Plan } from '../types';
import { getErrorMessage } from '../../../shared/lib/errors';
import { ErrorText } from '../../../shared/components/ErrorText';

interface PlansListProps {
  onSelectPlan: (plan: Plan) => void;
}

export function PlansList({ onSelectPlan }: PlansListProps) {
  const { data: plans, isLoading: plansLoading } = usePlans();
  const { data: subscription, isLoading: subLoading } = useMySubscription();
  const { mutateAsync: claimTrial, isPending: trialPending } = useClaimTrial();
  const [trialError, setTrialError] = useState('');

  if (plansLoading || subLoading) {
    return <div className="p-4 text-gray-500">Загрузка тарифов...</div>;
  }

  const handleTrial = async () => {
    setTrialError('');
    try {
      await claimTrial();
    } catch (err: unknown) {
      setTrialError(getErrorMessage(err));
    }
  };

  const trialUsed = subscription?.trialUsed ?? false;

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-bold">Тарифные планы</h2>
      
      <ErrorText error={trialError} className="bg-red-50 p-3 rounded mb-4 mt-0" />

      {!trialUsed && (
        <div className="bg-blue-50 border border-blue-200 rounded-lg p-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h3 className="font-bold text-blue-900">Пробный период</h3>
            <p className="text-sm text-blue-800 mt-1">Попробуйте BASIC бесплатно на несколько дней</p>
          </div>
          <button 
            onClick={handleTrial}
            disabled={trialPending}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 whitespace-nowrap"
          >
            Активировать пробный период
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {plans?.map((plan) => (
          <div key={plan.id} className="bg-white border border-gray-200 rounded-lg p-6 flex flex-col justify-between">
            <div>
              <h3 className="font-bold text-lg">{plan.code}</h3>
              <p className="text-sm text-gray-500 mt-1">Подписка {plan.tier} на {plan.durationDays} дней</p>
              <p className="text-2xl font-bold mt-4">{plan.price} {plan.currency}</p>
            </div>
            <button 
              onClick={() => onSelectPlan(plan)}
              className="mt-6 w-full px-4 py-2 border border-blue-600 text-blue-600 rounded hover:bg-blue-50"
            >
              Выбрать
            </button>
          </div>
        ))}
      </div>
      
      {plans?.length === 0 && (
        <p className="text-gray-500">Нет доступных тарифов.</p>
      )}
    </div>
  );
}
