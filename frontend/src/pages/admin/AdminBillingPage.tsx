import { useState } from 'react'
import {
  usePromos, useCreatePromo, useUpdatePromo,
  useTransactions, useRefundTransaction,
  useGrantSubscription, useUserSearch
} from '../../features/admin/hooks'
import { formatDateTime } from '../../shared/lib/format'

export function AdminBillingPage() {
  return (
    <div className="space-y-12">
      <section>
        <h2 className="text-xl font-bold mb-4">Промокоды</h2>
        <PromoCodesSection />
      </section>

      <section>
        <h2 className="text-xl font-bold mb-4">Транзакции</h2>
        <TransactionsSection />
      </section>

      <section>
        <h2 className="text-xl font-bold mb-4">Выдать подписку</h2>
        <GrantSubscriptionSection />
      </section>
    </div>
  )
}

function PromoCodesSection() {
  const { data: promos, isLoading } = usePromos()
  const createPromo = useCreatePromo()
  const updatePromo = useUpdatePromo()

  const [newPromo, setNewPromo] = useState({
    code: '',
    discountType: 'PERCENT' as 'PERCENT' | 'FIXED',
    discountValue: 10,
    maxUses: 100,
    validFrom: '',
    validUntil: ''
  })
  const [message, setMessage] = useState<{ type: 'error' | 'success', text: string } | null>(null)

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      await createPromo.mutateAsync({
        ...newPromo,
        validFrom: newPromo.validFrom ? new Date(newPromo.validFrom).toISOString() : undefined,
        validUntil: newPromo.validUntil ? new Date(newPromo.validUntil).toISOString() : undefined,
      })
      setMessage({ type: 'success', text: 'Промокод успешно создан' })
      setNewPromo({ ...newPromo, code: '' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при создании промокода' })
    }
  }

  return (
    <div className="space-y-4">
      <form onSubmit={handleCreate} className="bg-gray-50 p-4 border border-gray-200 rounded grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4 items-end">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Код</label>
          <input required type="text" value={newPromo.code} onChange={e => setNewPromo({...newPromo, code: e.target.value})} className="w-full border px-2 py-1 rounded" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Тип скидки</label>
          <select value={newPromo.discountType} onChange={e => setNewPromo({...newPromo, discountType: e.target.value as any})} className="w-full border px-2 py-1 rounded">
            <option value="PERCENT">Процент (%)</option>
            <option value="FIXED">Фиксированная</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Значение</label>
          <input required type="number" min={1} value={newPromo.discountValue} onChange={e => setNewPromo({...newPromo, discountValue: Number(e.target.value)})} className="w-full border px-2 py-1 rounded" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Макс. исп.</label>
          <input required type="number" min={1} value={newPromo.maxUses} onChange={e => setNewPromo({...newPromo, maxUses: Number(e.target.value)})} className="w-full border px-2 py-1 rounded" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Начало</label>
          <input type="datetime-local" value={newPromo.validFrom} onChange={e => setNewPromo({...newPromo, validFrom: e.target.value})} className="w-full border px-2 py-1 rounded" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Окончание</label>
          <input type="datetime-local" value={newPromo.validUntil} onChange={e => setNewPromo({...newPromo, validUntil: e.target.value})} className="w-full border px-2 py-1 rounded" />
        </div>
        <div className="col-span-1 md:col-span-2 lg:col-span-3 xl:col-span-6 flex flex-wrap items-center justify-between gap-2">
          {message ? <span className={`text-sm ${message.type === 'error' ? 'text-red-600' : 'text-green-600'}`}>{message.text}</span> : <span />}
          <button disabled={createPromo.isPending} type="submit" className="w-full sm:w-auto bg-blue-600 text-white px-4 py-1.5 rounded disabled:opacity-50 text-sm">Создать промокод</button>
        </div>
      </form>

      {isLoading && <div>Загрузка промокодов...</div>}
      {promos && promos.length === 0 && <div className="text-gray-500">Нет промокодов</div>}
      
      {promos && promos.length > 0 && (
        <div className="overflow-x-auto">
          <table className="min-w-[500px] w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Код</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Скидка</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Использования</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Действует</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Активен</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {promos.map(p => (
                <tr key={p.id}>
                  <td className="px-4 py-2 font-medium">{p.code}</td>
                  <td className="px-4 py-2">{p.discountValue} {p.discountType === 'PERCENT' ? '%' : 'руб.'}</td>
                  <td className="px-4 py-2">{p.usedCount} / {p.maxUses}</td>
                  <td className="px-4 py-2">
                    <div className="text-xs text-gray-500">С: {formatDateTime(p.validFrom)}</div>
                    <div className="text-xs text-gray-500">По: {formatDateTime(p.validUntil)}</div>
                  </td>
                  <td className="px-4 py-2">
                    <button 
                      onClick={() => updatePromo.mutate({ id: p.id, data: { isActive: !p.isActive } })}
                      className={`px-2 py-1 rounded text-xs text-white ${p.isActive ? 'bg-green-500' : 'bg-red-500'}`}
                      disabled={updatePromo.isPending}
                    >
                      {p.isActive ? 'ДА' : 'НЕТ'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function TransactionsSection() {
  const [filters, setFilters] = useState({
    userId: '',
    status: '',
    from: '',
    to: '',
    page: 0,
    size: 20
  })

  // To debounce filter submission
  const [appliedFilters, setAppliedFilters] = useState(filters)

  const { data: pageData, isLoading, error } = useTransactions(appliedFilters)
  const refundTransaction = useRefundTransaction()

  const [message, setMessage] = useState<{ type: 'error' | 'success', text: string } | null>(null)

  const handleApplyFilters = (e: React.FormEvent) => {
    e.preventDefault()
    setAppliedFilters({
      ...filters,
      from: filters.from ? new Date(filters.from).toISOString() : '',
      to: filters.to ? new Date(filters.to).toISOString() : ''
    })
  }

  const handleRefund = async (id: string) => {
    if (!window.confirm('Вы уверены, что хотите вернуть средства за эту транзакцию? Пользователю потребуется перезайти в систему.')) return
    
    try {
      await refundTransaction.mutateAsync(id)
      setMessage({ type: 'success', text: 'Средства успешно возвращены.' })
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при возврате' })
    }
  }

  return (
    <div className="space-y-4">
      <div className="bg-yellow-50 p-4 rounded text-sm text-yellow-800">
        <p><strong>Важно:</strong> При возврате средств пользователю необходимо перезайти в систему для обновления уровня подписки.</p>
      </div>

      <form onSubmit={handleApplyFilters} className="bg-gray-50 p-4 border border-gray-200 rounded flex flex-col sm:flex-row sm:flex-wrap gap-3 sm:gap-4 sm:items-end">
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">User ID</label>
          <input type="text" value={filters.userId} onChange={e => setFilters({...filters, userId: e.target.value})} className="w-full sm:w-40 border px-2 py-1 rounded text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">Статус</label>
          <select value={filters.status} onChange={e => setFilters({...filters, status: e.target.value})} className="w-full sm:w-32 border px-2 py-1 rounded text-sm">
            <option value="">Все</option>
            <option value="SUCCESS">SUCCESS</option>
            <option value="PENDING">PENDING</option>
            <option value="FAILED">FAILED</option>
            <option value="REFUNDED">REFUNDED</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">С (Дата)</label>
          <input type="date" value={filters.from} onChange={e => setFilters({...filters, from: e.target.value})} className="w-full sm:w-36 border px-2 py-1 rounded text-sm" />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-700 mb-1">По (Дата)</label>
          <input type="date" value={filters.to} onChange={e => setFilters({...filters, to: e.target.value})} className="w-full sm:w-36 border px-2 py-1 rounded text-sm" />
        </div>
        <button type="submit" className="w-full sm:w-auto bg-gray-200 hover:bg-gray-300 px-4 py-1.5 rounded text-sm">Фильтровать</button>
      </form>

      {message && <div className={`text-sm ${message.type === 'error' ? 'text-red-600' : 'text-green-600'}`}>{message.text}</div>}
      {error && <div className="text-red-600 text-sm">Ошибка загрузки транзакций: {(error as any)?.message}</div>}

      {isLoading ? <div>Загрузка...</div> : (
        <div className="overflow-x-auto">
          <table className="min-w-[600px] w-full divide-y divide-gray-200 text-sm">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Дата</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">User ID</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">План/Тип</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Статус</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Сумма</th>
                <th className="px-4 py-2 text-left font-medium text-gray-500">Действия</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {pageData?.content.map(t => (
                <tr key={t.id}>
                  <td className="px-4 py-2 whitespace-nowrap">{formatDateTime(t.createdAt)}</td>
                  <td className="px-4 py-2 font-mono text-xs">{t.userId.substring(0, 8)}...</td>
                  <td className="px-4 py-2">{t.planCode} <span className="text-gray-400 text-xs">({t.type})</span></td>
                  <td className="px-4 py-2">
                    <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full 
                      ${t.status === 'SUCCESS' ? 'bg-green-100 text-green-800' : 
                        t.status === 'FAILED' ? 'bg-red-100 text-red-800' : 
                        t.status === 'REFUNDED' ? 'bg-gray-100 text-gray-800' : 'bg-yellow-100 text-yellow-800'}`}>
                      {t.status}
                    </span>
                  </td>
                  <td className="px-4 py-2">{t.amount} {t.currency}</td>
                  <td className="px-4 py-2">
                    {t.status === 'SUCCESS' && t.type !== 'REFUND' && t.amount > 0 && (
                      <button 
                        onClick={() => handleRefund(t.id)}
                        disabled={refundTransaction.isPending}
                        className="text-red-600 hover:text-red-900 text-xs font-medium"
                      >
                        Возврат
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {pageData?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-4 text-center text-gray-500">Транзакции не найдены</td>
                </tr>
              )}
            </tbody>
          </table>
          
          {/* Pagination simple controls */}
          {pageData && pageData.page.totalPages > 1 && (
            <div className="flex justify-between items-center mt-4">
              <button 
                disabled={pageData.page.number === 0}
                onClick={() => setAppliedFilters({ ...appliedFilters, page: pageData.page.number - 1 })}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Назад
              </button>
              <span className="text-sm">Страница {pageData.page.number + 1} из {pageData.page.totalPages}</span>
              <button 
                disabled={pageData.page.number >= pageData.page.totalPages - 1}
                onClick={() => setAppliedFilters({ ...appliedFilters, page: pageData.page.number + 1 })}
                className="px-3 py-1 border rounded disabled:opacity-50"
              >
                Вперед
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function GrantSubscriptionSection() {
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  
  const { data: users, isLoading } = useUserSearch(debouncedSearch)
  const grantSub = useGrantSubscription()

  const [selectedUserId, setSelectedUserId] = useState('')
  const [days, setDays] = useState(30)
  const [note, setNote] = useState('')
  const [message, setMessage] = useState<{ type: 'error' | 'success', text: string } | null>(null)

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setDebouncedSearch(searchTerm)
  }

  const handleGrant = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedUserId) {
      setMessage({ type: 'error', text: 'Выберите пользователя или введите ID' })
      return
    }

    try {
      await grantSub.mutateAsync({ userId: selectedUserId, data: { days, note } })
      setMessage({ type: 'success', text: 'Подписка успешно выдана. Пользователю необходимо перезайти в систему.' })
      setSelectedUserId('')
      setNote('')
    } catch (err: any) {
      setMessage({ type: 'error', text: err?.response?.data?.message || 'Ошибка при выдаче подписки' })
    }
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
      <div>
        <h3 className="text-sm font-bold mb-2">Поиск пользователя (опционально)</h3>
        <form onSubmit={handleSearchSubmit} className="flex gap-2 mb-4">
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Имя пользователя..."
            className="flex-1 border px-3 py-1.5 rounded text-sm"
          />
          <button type="submit" className="bg-gray-200 px-4 py-1.5 rounded text-sm hover:bg-gray-300">
            Искать
          </button>
        </form>
        
        {isLoading && <div className="text-sm">Загрузка...</div>}
        <div className="space-y-2 max-h-64 overflow-y-auto">
          {users?.map(u => (
            <div 
              key={u.id} 
              onClick={() => setSelectedUserId(u.id)}
              className={`p-2 border rounded cursor-pointer flex items-center gap-2 ${selectedUserId === u.id ? 'bg-blue-50 border-blue-300' : 'hover:bg-gray-50'}`}
            >
              <img src={u.avatarUrl || `https://api.dicebear.com/7.x/identicon/svg?seed=${u.id}`} alt="" className="w-6 h-6 rounded-full" />
              <div className="text-sm">{u.username}</div>
              <div className="text-xs text-gray-400 ml-auto font-mono truncate w-24" title={u.id}>{u.id}</div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h3 className="text-sm font-bold mb-2">Форма выдачи (BASIC)</h3>
        <form onSubmit={handleGrant} className="space-y-4 bg-gray-50 p-4 border rounded">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">User ID</label>
            <input 
              required 
              type="text" 
              value={selectedUserId} 
              onChange={e => setSelectedUserId(e.target.value)} 
              placeholder="UUID пользователя"
              className="w-full border px-3 py-1.5 rounded text-sm font-mono" 
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Дней</label>
            <input 
              required 
              type="number" 
              min={1} 
              value={days} 
              onChange={e => setDays(Number(e.target.value))} 
              className="w-full border px-3 py-1.5 rounded text-sm" 
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Примечание (опц.)</label>
            <input 
              type="text" 
              value={note} 
              onChange={e => setNote(e.target.value)} 
              placeholder="Причина выдачи..."
              className="w-full border px-3 py-1.5 rounded text-sm" 
            />
          </div>
          <button 
            type="submit" 
            disabled={grantSub.isPending}
            className="w-full bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700 disabled:opacity-50"
          >
            Выдать подписку
          </button>
          {message && <div className={`text-sm mt-2 ${message.type === 'error' ? 'text-red-600' : 'text-green-600'}`}>{message.text}</div>}
        </form>
      </div>
    </div>
  )
}
