import Foundation
import StoreKit
import ComposeApp

/// StoreKit 2 implementation of the Kotlin `IosBillingBridge` (billing.md §10). Products: `in.sarkarimatch.pro.yearly`
/// (auto-renewable, 14-day intro offer) and `in.sarkarimatch.pro.season4m` (non-renewing). The JWS of each verified
/// transaction goes to the backend (App Store Server API); transactions are finished after the backend confirms (FE-24).
final class StoreKitBillingBridge: NSObject, IosBillingBridge {
    private var products: [String: Product] = [:]
    private var updatesTask: Task<Void, Never>?

    override init() {
        super.init()
        // Renewals, Ask to Buy approvals and purchases made on another device arrive here (FE-24 forwards them).
        updatesTask = Task.detached {
            for await _ in Transaction.updates {}
        }
    }

    func loadProducts(storeProductIds: [String], callback: IosProductsCallback) {
        Task { @MainActor in
            do {
                let loaded = try await Product.products(for: storeProductIds)
                loaded.forEach { self.products[$0.id] = $0 }
                callback.onSuccess(products: loaded.map { product in
                    IosStoreProduct(
                        storeProductId: product.id,
                        displayName: product.displayName,
                        displayPrice: product.displayPrice,
                        priceMicros: NSDecimalNumber(decimal: product.price * 1_000_000).int64Value,
                        currencyCode: product.priceFormatStyle.currencyCode,
                        period: product.subscription.map { Self.iso8601($0.subscriptionPeriod) }
                    )
                })
            } catch {
                callback.onError(message: error.localizedDescription)
            }
        }
    }

    func purchase(storeProductId: String, appAccountToken: String?, callback: IosPurchaseCallback) {
        Task { @MainActor in
            guard let product = self.products[storeProductId] else {
                callback.onError(message: "loadProducts() first")
                return
            }
            var options: Set<Product.PurchaseOption> = []
            if let token = appAccountToken, let uuid = UUID(uuidString: token) {
                options.insert(.appAccountToken(uuid))
            }
            do {
                switch try await product.purchase(options: options) {
                case .success(let verification):
                    switch verification {
                    case .verified(let transaction):
                        callback.onPurchased(transaction: IosTransaction(
                            storeProductId: transaction.productID,
                            jwsRepresentation: verification.jwsRepresentation
                        ))
                    case .unverified(_, let error):
                        callback.onError(message: error.localizedDescription)
                    }
                case .pending:
                    callback.onPending()
                case .userCancelled:
                    callback.onCancelled()
                @unknown default:
                    callback.onCancelled()
                }
            } catch {
                callback.onError(message: error.localizedDescription)
            }
        }
    }

    func currentEntitlements(callback: IosTransactionsCallback) {
        Task {
            var transactions: [IosTransaction] = []
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result {
                    transactions.append(IosTransaction(
                        storeProductId: transaction.productID,
                        jwsRepresentation: result.jwsRepresentation
                    ))
                }
            }
            callback.onResult(transactions: transactions)
        }
    }

    private static func iso8601(_ period: Product.SubscriptionPeriod) -> String {
        switch period.unit {
        case .day: return "P\(period.value)D"
        case .week: return "P\(period.value)W"
        case .month: return "P\(period.value)M"
        case .year: return "P\(period.value)Y"
        @unknown default: return "P\(period.value)D"
        }
    }
}
