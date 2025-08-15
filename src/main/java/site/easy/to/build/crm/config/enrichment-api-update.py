"""
Enrichment API Updates

This file contains the changes needed to update the enrichment_api.py file to use
the advanced website discovery system. You'll need to integrate these changes
into your existing enrichment_api.py file.
"""

# Add these imports at the top of your enrichment_api.py file
import os
import sys

# Add this after the other imports in your enrichment_api.py file
# Ensure the website_discovery_adapter module is in the path
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(current_dir)

# Import the adapter (adjust the import path as needed)
try:
    from website_discovery_adapter import WebsiteDiscoveryAdapter
    logger.info("Successfully imported WebsiteDiscoveryAdapter")
    USE_ADVANCED_WEBSITE_DISCOVERY = True
except ImportError as e:
    logger.warning(f"Failed to import WebsiteDiscoveryAdapter: {e}")
    logger.warning("Falling back to basic website discovery")
    USE_ADVANCED_WEBSITE_DISCOVERY = False

# Initialize the adapter
try:
    website_discovery_adapter = WebsiteDiscoveryAdapter(
        use_direct_import=True,
        use_browser=True,
        use_proxies=True
    ) if USE_ADVANCED_WEBSITE_DISCOVERY else None
    if USE_ADVANCED_WEBSITE_DISCOVERY:
        logger.info("Successfully initialized WebsiteDiscoveryAdapter")
except Exception as e:
    logger.warning(f"Failed to initialize WebsiteDiscoveryAdapter: {e}")
    logger.warning("Falling back to basic website discovery")
    USE_ADVANCED_WEBSITE_DISCOVERY = False
    website_discovery_adapter = None

# Replace the website enrichment part in run_enrichment_process function
# Find this section in run_enrichment_process where it builds the command for website enrichment

# Original code (starting around line 120-140):
"""
# Special handling for website enrichment script
if 'website_' in script_path or 'company_website_enricher' in script_path:
    # These scripts need special argument handling
    cmd = [
        sys.executable,
        script_path,
        '--csv', input_file,
        '--output', output_file,
        '--real'  # Force real mode
    ]
    # Add path to database
    db_path = os.path.join(os.getcwd(), 'company_data.db')
    if os.path.exists(db_path):
        cmd.extend(['--db', db_path])
        
    logger.info(f"Using custom arguments for website enrichment: {' '.join(cmd)}")
"""

# Replace with this code:
"""
# Special handling for website enrichment
if 'website_' in script_path or 'company_website_enricher' in script_path:
    # Check if we should use the advanced website discovery system
    if USE_ADVANCED_WEBSITE_DISCOVERY and website_discovery_adapter:
        logger.info("Using advanced website discovery system")
        
        try:
            # Load company data from input file
            with open(input_file, 'r') as f:
                input_data = json.load(f)
            
            # Get company data
            company_data = []
            
            # Try to get from 'companies' field first
            if 'companies' in input_data and isinstance(input_data['companies'], list):
                company_data = input_data['companies']
            # Fallback to company_ids
            elif 'company_ids' in input_data and isinstance(input_data['company_ids'], list):
                # Try to get company data from database
                company_ids = input_data['company_ids']
                
                # Convert to proper company objects
                for company_id in company_ids:
                    company_data.append({
                        'company_number': company_id,
                        # We'll leave name empty - adapter will try to fetch from DB
                    })
            
            if company_data:
                # Use the adapter to discover websites
                discovery_results = website_discovery_adapter.discover_websites(
                    company_data, 
                    output_file
                )
                
                # Success! Update job status directly
                if discovery_results:
                    succeeded = sum(1 for r in discovery_results if r.get('success'))
                    failed = len(discovery_results) - succeeded
                    
                    # Update job details
                    jobs[job_id]['status'] = 'completed'
                    jobs[job_id]['progress'] = 100
                    jobs[job_id]['results_file'] = output_file
                    jobs[job_id]['results'] = discovery_results
                    jobs[job_id]['details']['succeeded'] = succeeded
                    jobs[job_id]['details']['failed'] = failed
                    jobs[job_id]['details']['completed'] = len(discovery_results)
                    
                    # Calculate success rate
                    if (succeeded + failed) > 0:
                        success_rate = (succeeded / (succeeded + failed)) * 100
                        jobs[job_id]['details']['success_rate'] = success_rate
                    
                    # Calculate elapsed time
                    elapsed_time = time.time() - start_time
                    jobs[job_id]['details']['time_elapsed'] = elapsed_time
                    
                    logger.info(f"Advanced website discovery completed for job {job_id}")
                    logger.info(f"Found {succeeded} websites out of {len(discovery_results)} companies")
                    
                    return  # Exit the function early - we're done!
                else:
                    logger.warning("Advanced website discovery returned no results, falling back to standard method")
            else:
                logger.warning("No company data found in input file, falling back to standard method")
        except Exception as e:
            logger.error(f"Error in advanced website discovery: {e}")
            logger.warning("Falling back to standard website discovery")
    
    # Fallback to standard method
    cmd = [
        sys.executable,
        script_path,
        '--csv', input_file,
        '--output', output_file,
        '--real'  # Force real mode
    ]
    
    # Add path to database
    db_path = os.path.join(os.getcwd(), 'company_data.db')
    if os.path.exists(db_path):
        cmd.extend(['--db', db_path])
        
    logger.info(f"Using standard website enrichment: {' '.join(cmd)}")
"""
