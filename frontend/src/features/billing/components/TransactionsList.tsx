import { useMyTransactions } from '../hooks';
import { formatDate } from '../../../shared/lib/format';

export function TransactionsList() {
  const { data: transactions, isLoading } = useMyTransactions();

  if (isLoading) {
    return <div className="p-4 text-gray-500">Загрузка истории...</div>;
  }

  if (!transactions || transactions.length === 0) {
    return (
      <div className="bg-white border border-gray-200 rounded-lg p-6">
        <h2 className="text-xl font-bold mb-2">История платежей</h2>
        <p className="text-gray-600">У вас пока нет транзакций.</p>
      </div>
    );
  }

  return (
    <div className="bg-white border border-gray-200 rounded-lg p-6 overflow-hidden">
      <h2 className="text-xl font-bold mb-4">История платежей</h2>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-50 text-gray-600 border-b border-gray-200">
            <tr>
              <th className="px-4 py-3 font-medium">Дата</th>
              <th className="px-4 py-3 font-medium">Тип</th>
              <th className="px-4 py-3 font-medium">План</th>
              <th className="px-4 py-3 font-medium text-right">Сумма</th>
              <th className="px-4 py-3 font-medium text-right">Статус</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {transactions.map(txn => (
              <tr key={txn.id} className="hover:bg-gray-50">
                <td className="px-4 py-3 text-gray-500">{formatDate(txn.createdAt)}</td>
                <td className="px-4 py-3">{txn.type}</td>
                <td className="px-4 py-3">{txn.planCode || '-'}</td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  {txn.amount} {txn.currency}
                </td>
                <td className="px-4 py-3 text-right">
                  <span className={`px-2 py-1 rounded text-xs font-bold
                    ${txn.status === 'SUCCESS' ? 'bg-green-100 text-green-800' : 
                      txn.status === 'FAILED' ? 'bg-red-100 text-red-800' : 
                      'bg-gray-100 text-gray-800'}`}
                  >
                    {txn.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
