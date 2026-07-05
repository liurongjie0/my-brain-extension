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

// Delivery dates are generated relative to the current time so the demo
// scenarios (inside/outside the 30-day refund window) stay valid on any date.
function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 86_400_000).toISOString();
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
    deliveredAt: isoDaysAgo(5),
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
    deliveredAt: isoDaysAgo(4),
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
    deliveredAt: isoDaysAgo(65),
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
