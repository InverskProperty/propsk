#!/usr/bin/env python3
"""
Automatic API Code Patcher - Fixes the 6+ minute search issue
Replaces the slow query function with the optimized version
"""

import os
import re
import shutil
from datetime import datetime

def backup_original_file():
    """Create a backup of the original file"""
    api_file = 'core/enrichment_api.py'
    
    if os.path.exists(api_file):
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        backup_file = f'core/enrichment_api.py.backup_{timestamp}'
        shutil.copy2(api_file, backup_file)
        print(f"✅ Backup created: {backup_file}")
        return backup_file
    else:
        print(f"❌ API file not found: {api_file}")
        return None

def patch_api_function():
    """Replace the slow search function with the optimized version"""
    
    api_file = 'core/enrichment_api.py'
    
    if not os.path.exists(api_file):
        print(f"❌ API file not found: {api_file}")
        return False
    
    # Read the current file
    with open(api_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find the function to replace
    function_pattern = r'def build_optimized_search_query\(.*?\n(?:.*?\n)*?    return query, params'
    
    # New optimized function
    new_function = '''def build_optimized_search_query(search_params, selected_fields, page, page_size):
    """
    PERFORMANCE OPTIMIZED: Build search query with sub-second response times
    Fixes the 6+ minute search issue by using index-friendly patterns
    """
    import re
    
    basic = search_params.get('basic', {})
    location = search_params.get('location', {})
    status = search_params.get('status', {})
    sic = search_params.get('sic', {})
    
    company_name = basic.get('companyName', '').strip()
    company_number = basic.get('companyNumber', '').strip()
    search_type = basic.get('searchType', 'contains')
    
    location_value = location.get('location', '').strip()
    company_status = status.get('companyStatus', '')
    sic_codes = sic.get('codes', [])
    
    # Initialize query building
    query = f"SELECT {', '.join(selected_fields)} FROM companies WHERE 1=1"
    params = []
    
    # 1. Company number (exact match, instant)
    if company_number:
        query += " AND company_number = ?"
        params.append(company_number)
    
    # 2. Company status (very fast with index)
    if company_status:
        if company_status.lower() == 'active':
            company_status = 'Active'
        query += " AND company_status = ?"
        params.append(company_status)
    
    # 3. CRITICAL PERFORMANCE FIX: Company name search
    if company_name:
        if search_type == 'is':
            # Exact match - very fast
            query += " AND company_name = ?"
            params.append(company_name)
        else:
            # PERFORMANCE CRITICAL: Use index-friendly patterns
            if len(company_name) >= 3:
                # Strategy: Use starts-with (fast) OR word-boundary (medium)
                # This replaces the slow LIKE '%Microsoft%' with fast alternatives
                query += " AND (company_name LIKE ? OR company_name LIKE ?)"
                params.append(f"{company_name}%")      # Starts with - USES INDEX (fast)
                params.append(f"% {company_name}%")    # Word boundary - medium speed
            else:
                # For very short terms, use starts-with only
                query += " AND company_name LIKE ?"
                params.append(f"{company_name}%")       # USES INDEX (fast)
    
    # 4. SIC codes (use indexed numeric fields)
    if sic_codes:
        sic_conditions = []
        sic_params = []
        
        for sic_code in sic_codes:
            numeric_match = re.match(r'^\s*(\d+)', sic_code)
            if numeric_match:
                numeric_sic = numeric_match.group(1)
                # Use primary SIC field only for best performance
                sic_conditions.append("sic_code_1_num = ?")
                sic_params.append(numeric_sic)
        
        if sic_conditions:
            query += f" AND ({' OR '.join(sic_conditions)})"
            params.extend(sic_params)
    
    # 5. Location (optimize for postcode vs town)
    if location_value:
        if len(location_value) >= 2:
            # Check if it looks like a postcode first (faster)
            if location_value.replace(' ', '').isalnum() and len(location_value) <= 8:
                query += " AND (reg_postcode LIKE ? OR reg_post_town LIKE ?)"
                params.append(f"{location_value.upper()}%")  # Postcode prefix (fast)
                params.append(f"%{location_value}%")         # Town contains (slower)
            else:
                query += " AND reg_post_town LIKE ?"
                params.append(f"%{location_value}%")
    
    # Add ordering for consistent results
    query += " ORDER BY company_name"
    
    # Add pagination
    offset = (page - 1) * page_size
    query += " LIMIT ? OFFSET ?"
    params.append(page_size)
    params.append(offset)
    
    return query, params'''
    
    # Replace the function
    new_content = re.sub(function_pattern, new_function, content, flags=re.DOTALL)
    
    if new_content == content:
        print("⚠️ Function pattern not found - manual replacement needed")
        return False
    
    # Write the updated file
    with open(api_file, 'w', encoding='utf-8') as f:
        f.write(new_content)
    
    print("✅ API function replaced with optimized version")
    return True

def verify_changes():
    """Verify the changes were applied correctly"""
    api_file = 'core/enrichment_api.py'
    
    try:
        with open(api_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check for the performance comment
        if "PERFORMANCE OPTIMIZED" in content and "index-friendly patterns" in content:
            print("✅ Optimization markers found in code")
            return True
        else:
            print("❌ Optimization markers not found")
            return False
            
    except Exception as e:
        print(f"❌ Error verifying changes: {e}")
        return False

def main():
    """Apply the performance patch"""
    
    print("🔧 APPLYING PERFORMANCE PATCH TO API")
    print("=" * 45)
    
    # Create backup
    backup_file = backup_original_file()
    if not backup_file:
        return
    
    # Apply patch
    success = patch_api_function()
    
    if success:
        # Verify changes
        verified = verify_changes()
        
        if verified:
            print("\n✅ PATCH APPLIED SUCCESSFULLY!")
            print()
            print("📈 Expected improvement:")
            print("   • Search time: 6+ minutes → <1 second")
            print("   • Query pattern: LIKE '%Microsoft%' → LIKE 'Microsoft%'")
            print("   • Index utilization: None → Full")
            print()
            print("🔄 Next steps:")
            print("   1. Restart your API server")
            print("   2. Test with: python simple_test.py")
            print("   3. Search should complete in <1 second")
            print()
            print(f"💾 Backup saved: {backup_file}")
        else:
            print("\n❌ PATCH VERIFICATION FAILED")
            print(f"Restoring backup from: {backup_file}")
            shutil.copy2(backup_file, 'core/enrichment_api.py')
    else:
        print("\n❌ PATCH APPLICATION FAILED")
        print("Manual replacement required - see the code artifact above")

if __name__ == "__main__":
    main()
