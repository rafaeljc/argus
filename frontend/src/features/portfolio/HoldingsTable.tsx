import { formatMoney, formatPercent } from '../../shared/lib/money';
import { StatusBadge } from './StatusBadge';
import type { Position } from './types';

export interface HoldingsTableProps {
  positions: Position[];
}

export function HoldingsTable({ positions }: HoldingsTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full min-w-max text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">
              Ticker
            </th>
            <th scope="col" className="px-4 py-3">
              Quantity
            </th>
            <th scope="col" className="px-4 py-3">
              Last close
            </th>
            <th scope="col" className="px-4 py-3">
              Close date
            </th>
            <th scope="col" className="px-4 py-3">
              Position value
            </th>
            <th scope="col" className="px-4 py-3">
              % of portfolio
            </th>
            <th scope="col" className="px-4 py-3">
              Status
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {positions.map((position) => (
            <tr key={position.ticker}>
              <td className="px-4 py-3 font-medium text-slate-900">{position.ticker}</td>
              <td className="px-4 py-3">{position.quantity}</td>
              <td className="px-4 py-3">
                {position.last_close_price === null
                  ? '—'
                  : formatMoney(position.last_close_price, 'USD')}
              </td>
              <td className="px-4 py-3">{position.last_close_date ?? '—'}</td>
              <td className="px-4 py-3">
                {position.position_value === null
                  ? '—'
                  : formatMoney(position.position_value, 'USD')}
              </td>
              <td className="px-4 py-3">
                {position.percent_of_portfolio === null
                  ? '—'
                  : formatPercent(position.percent_of_portfolio)}
              </td>
              <td className="px-4 py-3">
                <StatusBadge position={position} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
