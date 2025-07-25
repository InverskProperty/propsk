// PayPropTestPageController.java
package site.easy.to.build.crm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller to serve the PayProp integration test page
 */
@Controller
@RequestMapping("/payprop")
public class PayPropTestPageController {
    
    /**
     * Serve the PayProp test page
     * Access at: /payprop/test
     */
    @GetMapping("/test")
    public String showTestPage() {
        return "payprop/test"; // This will look for test.html in templates/payprop/
    }
}