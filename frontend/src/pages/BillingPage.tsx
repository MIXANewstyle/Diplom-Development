import { useState } from 'react';
import { SubscriptionStatusCard } from '../features/billing/components/SubscriptionStatusCard';
import { PlansList } from '../features/billing/components/PlansList';
import { CheckoutPanel } from '../features/billing/components/CheckoutPanel';
import { TransactionsList } from '../features/billing/components/TransactionsList';
import type { Plan } from '../features/billing/types';

export function BillingPage() {
  const [selectedPlan, setSelectedPlan] = useState<Plan | null>(null);

  return (
    <div className="space-y-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold">Подписка и биллинг</h1>
      
      <SubscriptionStatusCard />

      {selectedPlan ? (
        <CheckoutPanel 
          plan={selectedPlan} 
          onCancel={() => setSelectedPlan(null)} 
        />
      ) : (
        <PlansList onSelectPlan={setSelectedPlan} />
      )}

      <TransactionsList />
    </div>
  );
}
