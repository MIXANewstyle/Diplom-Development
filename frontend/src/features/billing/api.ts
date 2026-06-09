import { apiClient } from '../../shared/api/client';
import type { 
  Plan, 
  Subscription, 
  Transaction, 
  CheckoutBody, 
  CheckoutResponse, 
  PromoValidationBody, 
  PromoValidationResponse 
} from './types';

export async function getPlans(): Promise<Plan[]> {
  const { data } = await apiClient.get<Plan[]>('/api/v1/billing/plans');
  return data;
}

export async function getMySubscription(): Promise<Subscription> {
  const { data } = await apiClient.get<Subscription>('/api/v1/billing/me/subscription');
  return data;
}

export async function getMyTransactions(): Promise<Transaction[]> {
  const { data } = await apiClient.get<Transaction[]>('/api/v1/billing/me/transactions');
  return data;
}

export async function checkout(body: CheckoutBody): Promise<CheckoutResponse> {
  const { data } = await apiClient.post<CheckoutResponse>('/api/v1/billing/subscriptions/checkout', body);
  return data;
}

export async function claimTrial(): Promise<Subscription> {
  const { data } = await apiClient.post<Subscription>('/api/v1/billing/subscriptions/trial');
  return data;
}

export async function validatePromo(body: PromoValidationBody): Promise<PromoValidationResponse> {
  const { data } = await apiClient.post<PromoValidationResponse>('/api/v1/billing/promo/validate', body);
  return data;
}

export async function confirmStubPayment(transactionId: string): Promise<void> {
  await apiClient.post(`/api/v1/billing/payments/stub/confirm/${transactionId}`);
}
