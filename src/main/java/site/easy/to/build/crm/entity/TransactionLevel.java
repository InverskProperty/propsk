package site.easy.to.build.crm.entity;

/**
 * Transaction Level Enum - Defines the scope/level of a transaction
 *
 * Used to classify transactions by their organizational level:
 * - property: Single property transactions (most common)
 * - block: Block/building-level transactions affecting multiple properties
 * - owner: Owner-level transactions across their portfolio
 * - portfolio: Portfolio-wide transactions not specific to properties
 */
public enum TransactionLevel {
    /**
     * Property-level transaction - specific to a single property
     * Examples: Property council tax, property utilities, property repairs
     */
    property,

    /**
     * Block-level transaction - applies to a block/building with multiple properties
     * Examples: Block insurance, communal area maintenance, block management fees
     */
    block,

    /**
     * Owner-level transaction - applies to an owner across their portfolio
     * Examples: Owner profit distribution, owner tax payments, owner fees
     */
    owner,

    /**
     * Portfolio-wide transaction - applies to entire portfolio
     * Examples: Portfolio insurance, portfolio management fees, agency-level charges
     */
    portfolio;

    /**
     * Check if this transaction level requires a property reference
     */
    public boolean requiresProperty() {
        return this == property;
    }

    /**
     * Check if this transaction level can have a property reference
     */
    public boolean allowsProperty() {
        return this == property || this == block;
    }

    /**
     * Check if this transaction level requires a customer/owner reference
     */
    public boolean requiresCustomer() {
        return this == owner;
    }
}
