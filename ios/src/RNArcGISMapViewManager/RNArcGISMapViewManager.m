//
//  RNArcGISMapViewManager.m
//  SampleArcGIS
//
//  Created by David Galindo on 1/31/19.
//  Copyright © 2019 David Galindo. All rights reserved.
//  This is the objc-Bridge that connects Swift to React Native
//
#import <React/RCTBridgeModule.h>
#import <React/RCTViewManager.h>


@interface RCT_EXTERN_MODULE(RNArcGISMapViewManager, RCTViewManager)
  RCT_EXPORT_VIEW_PROPERTY(basemapUrl, NSString)
  RCT_EXPORT_VIEW_PROPERTY(initialMapCenter, NSArray)
  RCT_EXPORT_VIEW_PROPERTY(recenterIfGraphicTapped, BOOL)
  RCT_EXPORT_VIEW_PROPERTY(onSingleTap, RCTDirectEventBlock)
  RCT_EXPORT_VIEW_PROPERTY(onMapDidLoad, RCTDirectEventBlock)
  RCT_EXPORT_VIEW_PROPERTY(onOverlayWasModified, RCTDirectEventBlock)
  RCT_EXPORT_VIEW_PROPERTY(onOverlayWasAdded, RCTDirectEventBlock)
  RCT_EXPORT_VIEW_PROPERTY(onOverlayWasRemoved, RCTDirectEventBlock)

  // MARK: External method exports (these can be called from react via a reference)
  RCT_EXTERN_METHOD(showCalloutViaManager:(nonnull NSNumber*)node args:(NSDictionary*)args)
  RCT_EXTERN_METHOD(centerMapViaManager:(nonnull NSNumber*)node args:(NSArray*)args)
  RCT_EXTERN_METHOD(addGraphicsOverlayViaManager:(nonnull NSNumber*) node args:(NSDictionary*)args)
  RCT_EXTERN_METHOD(removeGraphicsOverlayViaManager:(nonnull NSNumber*) node args:(NSString*) args)
  RCT_EXTERN_METHOD(addPointsToOverlayViaManager:(nonnull NSNumber*) node args:(NSDictionary*)args)
  RCT_EXTERN_METHOD(removePointsFromOverlayViaManager:(nonnull NSNumber*) node args:(NSDictionary*)args)
  RCT_EXTERN_METHOD(updatePointsInGraphicsOverlayViaManager:(nonnull NSNumber*) node args:(NSDictionary*)args)
  RCT_EXTERN_METHOD(dispose:(nonnull NSNumber*) node)

@end