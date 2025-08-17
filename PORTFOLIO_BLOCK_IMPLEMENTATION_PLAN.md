# 🏗️ Portfolio-Block System Implementation Plan

## 📋 Sequential Task Breakdown with Dependencies

**Current Status**: ✅ Foundation stable - PayProp integration working
**Goal**: Implement hierarchical Portfolio → Block → Property system with PayProp sync

---

## 🏛️ **PHASE 1: Database Foundation** 
*Must be completed first - all other phases depend on this*

### Task 1.1: Design Block Entity Structure
**Dependencies**: None
**Estimated Time**: 2 hours
**Components**:
- [ ] Design `PortfolioBlock` entity class
- [ ] Define relationships: Portfolio (1:N) Block (1:N) Property
- [ ] Plan block hierarchy and naming conventions
- [ ] Design block metadata fields (name, description, display_order)

### Task 1.2: Create Database Migration Scripts
**Dependencies**: Task 1.1 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Create `portfolio_blocks` table migration
- [ ] Add foreign key relationships to existing tables
- [ ] Update `property_portfolio_assignments` to support blocks
- [ ] Add indexes for performance
- [ ] Create rollback scripts

### Task 1.3: Update Repository Layer
**Dependencies**: Task 1.2 complete
**Estimated Time**: 2 hours
**Components**:
- [ ] Create `PortfolioBlockRepository` interface
- [ ] Add block-related queries to existing repositories
- [ ] Update `PropertyPortfolioAssignmentRepository` for block support
- [ ] Add block finder methods and validation queries

---

## ⚙️ **PHASE 2: Core Business Logic Services**
*Depends on Phase 1 complete*

### Task 2.1: Block Creation Service
**Dependencies**: Phase 1 complete
**Estimated Time**: 4 hours
**Components**:
- [ ] Create `PortfolioBlockService` interface and implementation
- [ ] Implement `createBlock(portfolioId, blockName, description)` method
- [ ] Add block validation logic (unique names within portfolio)
- [ ] Implement block ordering and hierarchy management
- [ ] Add block CRUD operations (create, read, update, delete)

### Task 2.2: Hierarchical Tag Generation System
**Dependencies**: Task 2.1 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Design tag naming convention: `PF-{PORTFOLIO}-BL-{BLOCK}`
- [ ] Create `PayPropTagGenerator` utility class
- [ ] Implement portfolio tag standardization (ensure PF- prefix)
- [ ] Implement block tag generation with hierarchy validation
- [ ] Add tag cleanup and normalization methods

### Task 2.3: Block Assignment Logic
**Dependencies**: Task 2.2 complete
**Estimated Time**: 5 hours
**Components**:
- [ ] Update `PortfolioAssignmentService` to support blocks
- [ ] Implement `assignPropertyToBlock(propertyId, blockId)` method
- [ ] Add block reassignment logic (move properties between blocks)
- [ ] Implement bulk assignment operations
- [ ] Add validation for assignment conflicts

---

## 🔄 **PHASE 3: PayProp Integration**
*Depends on Phase 2 complete*

### Task 3.1: Block PayProp Sync Service
**Dependencies**: Phase 2 complete
**Estimated Time**: 6 hours
**Components**:
- [ ] Extend `PayPropSyncService` to support blocks
- [ ] Implement `syncBlockToPayProp(blockId)` method
- [ ] Add block tag creation in PayProp API
- [ ] Implement property reassignment in PayProp when moved between blocks
- [ ] Add block sync status tracking and error handling

### Task 3.2: Hierarchical Sync Logic
**Dependencies**: Task 3.1 complete
**Estimated Time**: 4 hours
**Components**:
- [ ] Implement portfolio → block sync cascading
- [ ] Add block tag dependency management (portfolio must exist first)
- [ ] Implement property tag removal/reassignment in PayProp
- [ ] Add sync rollback logic for failed block operations
- [ ] Implement batch sync for multiple blocks

### Task 3.3: Enhanced Migration Service
**Dependencies**: Task 3.2 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Update `PayPropPortfolioMigrationService` for blocks
- [ ] Add migration path: Portfolio-only → Portfolio+Blocks
- [ ] Implement default block creation for existing portfolios
- [ ] Add block sync validation and repair methods

---

## 🌐 **PHASE 4: API Layer & Controllers**
*Depends on Phase 3 complete*

### Task 4.1: Block Management API
**Dependencies**: Phase 3 complete
**Estimated Time**: 4 hours
**Components**:
- [ ] Create `PortfolioBlockController` with REST endpoints
- [ ] Implement `POST /portfolio/{id}/blocks` (create block)
- [ ] Implement `GET /portfolio/{id}/blocks` (list blocks)
- [ ] Implement `PUT /blocks/{id}` (update block)
- [ ] Implement `DELETE /blocks/{id}` (delete block)
- [ ] Add proper error handling and validation

### Task 4.2: Drag-and-Drop API Support
**Dependencies**: Task 4.1 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Implement `POST /blocks/{id}/assign-property` (assign to block)
- [ ] Implement `POST /blocks/reassign-property` (move between blocks)
- [ ] Add batch assignment endpoints for multiple properties
- [ ] Implement block reordering API (`PUT /blocks/reorder`)
- [ ] Add validation for drag-and-drop operations

### Task 4.3: Portfolio Hierarchy API
**Dependencies**: Task 4.2 complete
**Estimated Time**: 2 hours
**Components**:
- [ ] Update existing portfolio endpoints to include blocks
- [ ] Implement `GET /portfolio/{id}/hierarchy` (full tree view)
- [ ] Add block statistics endpoints (property counts, sync status)
- [ ] Implement search and filtering for blocks and properties

---

## 🎨 **PHASE 5: UI Implementation**
*Depends on Phase 4 complete*

### Task 5.1: Block Creation Interface
**Dependencies**: Phase 4 complete
**Estimated Time**: 5 hours
**Components**:
- [ ] Add "Create Block" button to portfolio pages
- [ ] Implement block creation modal/form
- [ ] Add block name validation and duplicate checking
- [ ] Implement block description and metadata editing
- [ ] Add success/error feedback for block operations

### Task 5.2: Hierarchical Portfolio View
**Dependencies**: Task 5.1 complete
**Estimated Time**: 6 hours
**Components**:
- [ ] Update portfolio view to show Portfolio → Blocks → Properties hierarchy
- [ ] Implement collapsible/expandable block sections
- [ ] Add block management controls (edit, delete, reorder)
- [ ] Show sync status indicators for blocks and properties
- [ ] Add block statistics display (property count, sync status)

### Task 5.3: Drag-and-Drop Functionality
**Dependencies**: Task 5.2 complete
**Estimated Time**: 8 hours
**Components**:
- [ ] Implement draggable property cards/rows
- [ ] Add drop zones for blocks within portfolios
- [ ] Implement visual feedback during drag operations
- [ ] Add drag-and-drop between different blocks
- [ ] Implement bulk property selection and movement
- [ ] Add undo functionality for accidental moves

### Task 5.4: Block Management Interface
**Dependencies**: Task 5.3 complete
**Estimated Time**: 4 hours
**Components**:
- [ ] Implement block editing interface (name, description)
- [ ] Add block deletion with property reassignment options
- [ ] Implement block reordering interface
- [ ] Add block duplication functionality
- [ ] Implement block templates/presets

---

## 🧪 **PHASE 6: Testing & Validation**
*Depends on Phase 5 complete*

### Task 6.1: Unit Testing
**Dependencies**: Phase 5 complete
**Estimated Time**: 6 hours
**Components**:
- [ ] Write unit tests for `PortfolioBlockService`
- [ ] Test hierarchical tag generation logic
- [ ] Test block assignment and reassignment methods
- [ ] Test PayProp sync logic for blocks
- [ ] Test API endpoint validation and error handling

### Task 6.2: Integration Testing
**Dependencies**: Task 6.1 complete
**Estimated Time**: 4 hours
**Components**:
- [ ] Test end-to-end block creation → PayProp sync flow
- [ ] Test property reassignment between blocks
- [ ] Test portfolio deletion with blocks cleanup
- [ ] Test migration scenarios (existing portfolios → blocks)
- [ ] Test concurrent operations and data consistency

### Task 6.3: PayProp Sync Validation
**Dependencies**: Task 6.2 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Validate hierarchical tags appear correctly in PayProp
- [ ] Test property movement between tags in PayProp
- [ ] Validate tag cleanup when blocks are deleted
- [ ] Test sync failure recovery and retry logic
- [ ] Validate sync status accuracy and reporting

### Task 6.4: Performance & Load Testing
**Dependencies**: Task 6.3 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Test performance with large numbers of blocks
- [ ] Test drag-and-drop performance with many properties
- [ ] Validate database query performance with indexing
- [ ] Test PayProp API rate limiting with bulk operations
- [ ] Optimize slow operations identified during testing

---

## 🚀 **PHASE 7: Production Deployment & Monitoring**
*Depends on Phase 6 complete*

### Task 7.1: Production Preparation
**Dependencies**: Phase 6 complete
**Estimated Time**: 3 hours
**Components**:
- [ ] Create production deployment checklist
- [ ] Prepare database migration scripts for production
- [ ] Update documentation for block system
- [ ] Create user training materials
- [ ] Set up monitoring for block operations

### Task 7.2: Gradual Rollout
**Dependencies**: Task 7.1 complete
**Estimated Time**: 2 hours
**Components**:
- [ ] Deploy behind feature flag for testing
- [ ] Enable for limited users first
- [ ] Monitor for issues and performance problems
- [ ] Collect user feedback and iterate
- [ ] Full rollout after validation

---

## 📊 **Summary & Timeline**

### **Total Estimated Time**: 77 hours (~2-3 weeks with 1 developer)

### **Critical Path Dependencies**:
1. **Phase 1** (Database) → **Phase 2** (Services) → **Phase 3** (PayProp) → **Phase 4** (API) → **Phase 5** (UI) → **Phase 6** (Testing) → **Phase 7** (Deploy)

### **Parallel Work Opportunities**:
- UI mockups/design can be done during Phase 1-3
- Documentation can be written during development phases
- Test planning can happen during Phase 2-3

### **Risk Mitigation**:
- Test PayProp integration early and frequently
- Validate database performance with large datasets
- Plan rollback strategy for each phase
- Keep debug endpoints for troubleshooting

### **Success Criteria**:
- [ ] Properties can be organized into blocks within portfolios
- [ ] Drag-and-drop works smoothly for property reassignment
- [ ] All block operations sync correctly to PayProp
- [ ] Performance is acceptable with realistic data volumes
- [ ] Users can easily manage complex portfolio hierarchies

---

## 🎯 **Immediate Next Steps**

**Ready to Start**: Task 1.1 - Design Block Entity Structure
**Prerequisites**: All foundation work complete ✅
**Estimated Time to First Working Feature**: ~2 weeks

Would you like to begin with **Task 1.1: Design Block Entity Structure**?