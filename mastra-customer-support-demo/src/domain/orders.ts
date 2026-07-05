export type OrderStatus = 'delivered' | 'processing' | 'cancelled' | 'refunded';

export interface Order {
  id: string;
  customerId: string;
  customerName: string;
  itemName: string;
  status: OrderStatus;
  totalCents: number;
  currency: 'USD';
  deliveredAt: string | null;
  refundHistoryCount: number;
}

export const mockOrders: Record<string, Order> = {
  ord_small_recent: {
    id: 'ord_small_recent',
    customerId: 'cus_ada',
    customerName: 'Ada Chen',
    itemName: 'Noise-canceling earbuds',
    status: 'delivered',
    totalCents: 3499,
    currency: 'USD',
    deliveredAt: '2026-06-30T12:00:00.000Z',
    refundHistoryCount: 0,
  },
  ord_high_value_recent: {
    id: 'ord_high_value_recent',
    customerId: 'cus_grace',
    customerName: 'Grace Hopper',
    itemName: 'Developer workstation bundle',
    status: 'delivered',
    totalCents: 129900,
    currency: 'USD',
    deliveredAt: '2026-07-01T12:00:00.000Z',
    refundHistoryCount: 0,
  },
  ord_old_delivery: {
    id: 'ord_old_delivery',
    customerId: 'cus_lin',
    customerName: 'Lin Zhou',
    itemName: 'Travel backpack',
    status: 'delivered',
    totalCents: 12999,
    currency: 'USD',
    deliveredAt: '2026-05-01T12:00:00.000Z',
    refundHistoryCount: 1,
  },
};

export function getMockOrder(orderId: string): Order {
  const order = mockOrders[orderId];
  if (!order) {
    throw new Error(`Order not found: ${orderId}`);
  }

  return order;
}
