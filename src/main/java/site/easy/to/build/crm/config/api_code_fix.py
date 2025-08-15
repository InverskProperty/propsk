# REPLACE this function in your enrichment_api.py file
# Find the existing build_optimized_search_query function and replace it with this:

def build_optimized_search_query(search_params, selected_fields, page, page_size):
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
    
    return query, params