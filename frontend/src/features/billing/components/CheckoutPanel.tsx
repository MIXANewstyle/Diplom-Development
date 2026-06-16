import { useState } from 'react';
import { useCheckout, useValidatePromo } from '../hooks';
import type { Plan } from '../types';
import { useNavigate } from 'react-router-dom';
import { getErrorMessage } from '../../../shared/lib/errors';
import { ErrorText } from '../../../shared/components/ErrorText';

interface CheckoutPanelProps {
  plan: Plan;
  onCancel: () => void;
}

export function CheckoutPanel({ plan, onCancel }: CheckoutPanelProps) {
  const [promoCode, setPromoCode] = useState('');
  const [promoError, setPromoError] = useState('');
  const [checkoutError, setCheckoutError] = useState('');
  const [finalAmount, setFinalAmount] = useState<number | null>(null);
  
  const { mutateAsync: validatePromo, isPending: validating } = useValidatePromo();
  const { mutateAsync: checkout, isPending: checkingOut } = useCheckout();
  const navigate = useNavigate();

  const handleValidatePromo = async () => {
    if (!promoCode.trim()) return;
    setPromoError('');
    try {
      const res = await validatePromo({ planId: plan.id, code: promoCode.trim() });
      if (res.valid) {
        setFinalAmount(res.finalAmount);
      } else {
        setPromoError('Промокод недействителен');
      }
    } catch (err: unknown) {
      setPromoError(getErrorMessage(err));
    }
  };

  const handleCheckout = async () => {
    setCheckoutError('');
    try {
      const res = await checkout({ 
        planId: plan.id, 
        promoCode: promoCode.trim() || undefined 
      });
      onCancel(); // Close panel
      navigate(`/billing/payment/${res.transactionId}`, {
        state: { planCode: plan.code, amount: displayAmount !== null ? displayAmount : plan.price, currency: plan.currency }
      });
    } catch (err: unknown) {
      setCheckoutError(getErrorMessage(err));
    }
  };

  const displayAmount = finalAmount !== null ? finalAmount : plan.price;
  const isBusy = validating || checkingOut;

  return (
    <div className="bg-white border border-blue-300 shadow-sm rounded-lg p-6">
      <h2 className="text-xl font-bold mb-4">Оформление подписки</h2>
      
      <div className="bg-gray-50 rounded p-4 mb-6">
        <h3 className="font-semibold">{plan.code}</h3>
        <div className="flex justify-between items-center mt-2">
          <span className="text-gray-600">Длительность:</span>
          <span>{plan.durationDays} дней</span>
        </div>
        <div className="flex justify-between items-center mt-1">
          <span className="text-gray-600">Сумма к оплате:</span>
          <span className="font-bold text-lg">{displayAmount} {plan.currency}</span>
        </div>
      </div>

      <div className="mb-6">
        <label className="block text-sm font-medium text-gray-700 mb-1">Промокод (необязательно)</label>
        <div className="flex gap-2">
          <input 
            type="text" 
            value={promoCode} 
            onChange={e => setPromoCode(e.target.value)}
            className="flex-1 border border-gray-300 rounded px-3 py-2 uppercase"
            placeholder="PROMO2026"
            disabled={isBusy}
          />
          <button 
            type="button" 
            onClick={handleValidatePromo}
            disabled={!promoCode.trim() || isBusy}
            className="px-4 py-2 border border-gray-300 rounded text-gray-700 hover:bg-gray-50 disabled:opacity-50"
          >
            Проверить
          </button>
        </div>
        <ErrorText error={promoError} />
        {finalAmount !== null && !promoError && (
          <p className="text-green-600 text-sm mt-1">Промокод применен!</p>
        )}
      </div>

      <ErrorText error={checkoutError} className="bg-red-50 p-3 rounded mb-4 mt-0" />

      <div className="flex flex-wrap gap-2 justify-end">
        <button 
          onClick={onCancel}
          disabled={isBusy}
          className="flex-1 sm:flex-none px-4 py-2 border border-gray-300 rounded text-gray-700 hover:bg-gray-50"
        >
          Отмена
        </button>
        <button 
          onClick={handleCheckout}
          disabled={isBusy}
          className="flex-1 sm:flex-none px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 flex items-center justify-center gap-2"
        >
          {checkingOut ? 'Обработка...' : `Оплатить ${displayAmount} ${plan.currency}`}
        </button>
      </div>
    </div>
  );
}
