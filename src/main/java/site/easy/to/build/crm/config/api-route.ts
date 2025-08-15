// app/api/property/search/route.ts
import { NextResponse } from 'next/server';

export async function POST(request: Request) {
  try {
    const { houseNumber, street, postcode } = await request.json();
    
    // Log incoming request data
    console.log('Search Request:', { houseNumber, street, postcode });

    // Validate input
    if (!houseNumber || !street || !postcode) {
      return NextResponse.json(
        { error: 'Missing required fields' },
        { status: 400 }
      );
    }

    // Format address for API
    const formattedAddress = `${houseNumber}, ${street}, ${postcode}`;
    console.log('Formatted Address:', formattedAddress);
    
    // Make first API call to get UPRN
    const uprnUrl = `https://api.propertydata.co.uk/address-to-uprn?key=${process.env.PROPERTY_DATA_API_KEY}&address=${encodeURIComponent(formattedAddress)}`;
    console.log('UPRN URL:', uprnUrl);
    
    const uprnResponse = await fetch(uprnUrl);
    const uprnData = await uprnResponse.json();
    console.log('UPRN Response:', uprnData);

    if (!uprnResponse.ok) {
      throw new Error(`UPRN lookup failed: ${uprnData.message || uprnResponse.statusText}`);
    }

    if (!uprnData.data?.[0]?.uprn) {
      return NextResponse.json(
        { error: 'Address not found. Please check the details and try again.' },
        { status: 404 }
      );
    }

    // Make second API call to get property details
    const propertyUrl = `https://api.propertydata.co.uk/uprn?key=${process.env.PROPERTY_DATA_API_KEY}&uprn=${uprnData.data[0].uprn}`;
    console.log('Property URL:', propertyUrl);
    
    const propertyResponse = await fetch(propertyUrl);
    const propertyData = await propertyResponse.json();
    console.log('Property Response:', propertyData);

    if (!propertyResponse.ok) {
      throw new Error(`Property lookup failed: ${propertyData.message || propertyResponse.statusText}`);
    }

    return NextResponse.json(propertyData);
  } catch (error) {
    console.error('Property search error:', error);
    return NextResponse.json(
      { error: error instanceof Error ? error.message : 'Failed to search property' },
      { status: 500 }
    );
  }
}