import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as api from '../api';
import type { CheckoutBody, PromoValidationBody } from '../types';

export function usePlans() {
  return useQuery({
    queryKey: ['billing', 'plans'],
    queryFn: api.getPlans,
    staleTime: 1000 * 60 * 60, // 1 hour
  });
}

export function useMySubscription() {
  return useQuery({
    queryKey: ['billing', 'subscription'],
    queryFn: api.getMySubscription,
    retry: false, // Do not retry to catch 404 quickly
  });
}

export function useMyTransactions() {
  return useQuery({
    queryKey: ['billing', 'transactions'],
    queryFn: api.getMyTransactions,
  });
}

export function useCheckout() {
  return useMutation({
    mutationFn: (body: CheckoutBody) => api.checkout(body),
  });
}

export function useClaimTrial() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.claimTrial(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
}

export function useValidatePromo() {
  return useMutation({
    mutationFn: (body: PromoValidationBody) => api.validatePromo(body),
  });
}

export function useConfirmStubPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (transactionId: string) => api.confirmStubPayment(transactionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billing'] });
    },
  });
}
