// Main application JavaScript file
// This file contains common functionality used across the application

$(document).ready(function() {
    console.log('Application JavaScript loaded successfully');

    // Initialize tooltips if Bootstrap is available
    if (typeof bootstrap !== 'undefined') {
        var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
        var tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
            return new bootstrap.Tooltip(tooltipTriggerEl);
        });
    }

    // Initialize any other common functionality here
});

// Common utility functions
window.App = window.App || {
    // Utility function to show alerts
    showAlert: function(message, type) {
        type = type || 'info';
        console.log('[' + type.toUpperCase() + '] ' + message);

        // You can enhance this to show actual UI alerts
        if (typeof alert !== 'undefined') {
            alert(message);
        }
    },

    // Utility function to format currency
    formatCurrency: function(amount) {
        return new Intl.NumberFormat('en-GB', {
            style: 'currency',
            currency: 'GBP'
        }).format(amount);
    }
};