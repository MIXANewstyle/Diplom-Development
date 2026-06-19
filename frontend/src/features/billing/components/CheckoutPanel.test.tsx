import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as billingApi from '../api'
import type { Plan } from '../types'
import { CheckoutPanel } from './CheckoutPanel'
import { renderWithProviders } from '../../../test/renderWithProviders'

const testPlan: Plan = {
  id: 1,
  code: 'BASIC_MONTH',
  tier: 'BASIC',
  durationDays: 30,
  price: 499,
  currency: 'RUB',
}

describe('CheckoutPanel', () => {
  it('отправляет данные оформления подписки при оплате', async () => {
    const checkoutMock = vi.fn().mockResolvedValue({
      transactionId: 'tx-1',
      status: 'PENDING',
      amount: 499,
      currency: 'RUB',
    })
    vi.spyOn(billingApi, 'checkout').mockImplementation(checkoutMock)
    const onCancel = vi.fn()

    renderWithProviders(<CheckoutPanel plan={testPlan} onCancel={onCancel} />)
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Оплатить 499 RUB' }))

    await waitFor(() => {
      expect(checkoutMock).toHaveBeenCalledWith({
        planId: 1,
        promoCode: undefined,
      })
    })
  })
})
