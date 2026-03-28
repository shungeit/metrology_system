<template>
  <div>
    <!-- 筛选栏 -->
    <div style="background:#fff;border:1px solid var(--border);border-radius:12px;padding:14px 16px;margin-bottom:12px">
      <div style="display:flex;flex-wrap:wrap;gap:8px;align-items:center">
        <el-input v-model="search" placeholder="仪器名称/计量编号/责任人" clearable size="default" style="width:210px" @input="onFilter" @clear="onFilter">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="filterAssetNo" placeholder="资产编号" clearable size="default" style="width:140px" @input="onFilter" @clear="onFilter" />
        <el-input v-model="filterSerialNo" placeholder="出厂编号" clearable size="default" style="width:140px" @input="onFilter" @clear="onFilter" />
        <el-select v-model="filterDept" placeholder="全部部门" clearable size="default" style="width:140px" @change="onFilter">
          <el-option v-for="d in DEPTS" :key="d" :value="d" :label="d" />
        </el-select>
        <el-select v-model="filterValidity" placeholder="有效性" clearable size="default" style="width:120px" @change="onFilter">
          <el-option value="有效" label="有效" />
          <el-option value="即将过期" label="即将过期" />
          <el-option value="失效" label="失效" />
        </el-select>
        <el-select v-model="filterUseStatus" placeholder="使用状态" clearable size="default" style="width:120px" @change="onFilter">
          <el-option v-for="s in deviceStatuses" :key="s.id" :value="s.name" :label="s.name" />
        </el-select>
        <el-button size="default" @click="resetFilter"><el-icon><RefreshLeft /></el-icon> 重置</el-button>
        <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap;align-items:center">
          <el-button size="default" @click="downloadTemplate"><el-icon><Download /></el-icon> 模板</el-button>
          <el-button size="default" @click="triggerImport"><el-icon><Upload /></el-icon> 导入</el-button>
          <input ref="importRef" type="file" style="display:none" accept=".xlsx,.xls" @change="handleImport" />
          <el-button size="default" @click="exportFiltered"><el-icon><Document /></el-icon> 导出当前</el-button>
          <el-button size="default" @click="exportAll"><el-icon><Document /></el-icon> 导出全部</el-button>
          <el-button v-if="authStore.canCreate" type="primary" size="default" @click="openModal()"><el-icon><Plus /></el-icon> 新增设备</el-button>
        </div>
      </div>
    </div>

    <div class="batch-bar">
      <div class="batch-info">
        已选 <b>{{ selectedIds.length }}</b> 项
      </div>
      <div class="batch-actions">
        <el-button size="small" @click="toggleSelectCurrentPage">
          {{ allCurrentPageSelected ? '取消当前页' : '全选当前页' }}
        </el-button>
        <el-button size="small" @click="clearSelection" :disabled="!selectedIds.length">清空选择</el-button>
        <el-button v-if="authStore.canUpdate" size="small" @click="openBatchEdit('dept')" :disabled="!selectedIds.length">批量改部门</el-button>
        <el-button v-if="authStore.canUpdate" size="small" @click="openBatchEdit('responsiblePerson')" :disabled="!selectedIds.length">批量改责任人</el-button>
        <el-button v-if="authStore.canUpdate" size="small" @click="openBatchEdit('location')" :disabled="!selectedIds.length">批量改位置</el-button>
        <el-button v-if="authStore.canDelete" size="small" type="danger" @click="deleteSelected" :disabled="!selectedIds.length">批量删除</el-button>
      </div>
    </div>

    <!-- 桌面表格 -->
    <div class="table-wrap">
      <div class="table-scroll">
        <table>
          <thead>
            <tr>
              <th style="width:54px">
                <input type="checkbox" :checked="allCurrentPageSelected" @change="toggleSelectCurrentPage" />
              </th>
              <th>仪器名称</th><th>计量编号</th><th>使用部门</th><th>使用责任人</th>
              <th>上次校准</th><th>下次校准</th><th>有效性</th><th>使用状态</th>
              <th>设备图片</th><th>校准证书</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="paged.length===0" class="empty-row">
              <td colspan="12">{{ loading ? '加载中...' : '暂无设备数据，点击「新增设备」开始添加' }}</td>
            </tr>
            <tr v-for="d in paged" :key="d.id">
              <td>
                <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
              </td>
              <td><span style="font-weight:500;cursor:pointer;color:var(--primary)" @click="openPreview(d)">{{ d.name }}</span></td>
              <td><code style="font-size:12px;background:#f1f5f9;padding:2px 6px;border-radius:4px">{{ d.metricNo }}</code></td>
              <td>{{ d.dept || '-' }}</td>
              <td>{{ d.responsiblePerson || '-' }}</td>
              <td>{{ d.calDate || '-' }}</td>
              <td :style="{ color: d.validity==='失效' ? 'var(--danger)' : d.validity==='即将过期' ? 'var(--warning)' : 'inherit', fontWeight: d.validity!=='有效'?600:'normal' }">
                {{ d.nextDate || '-' }}
              </td>
              <td><span :class="['tag', validityTag(d.validity)]">{{ d.validity || '-' }}</span></td>
              <td><span :class="['tag', useStatusTag(d.useStatus)]">{{ d.useStatus || '正常' }}</span></td>
              <td>
                <img v-if="d.imagePath" :src="d.imagePath" class="table-img" @click="openImg(d.imagePath)" />
                <span v-else class="text-muted text-sm">-</span>
              </td>
              <td>
                <a v-if="d.certPath" :href="d.certPath" :download="d.certName||'cert'" class="table-link">📥 下载</a>
                <span v-else class="text-muted text-sm">-</span>
              </td>
              <td>
                <div class="action-group">
                  <button class="action-btn action-btn-view" @click="openPreview(d)">预览</button>
                  <button v-if="authStore.canUpdate" class="action-btn action-btn-edit" @click="openModal(d)">编辑</button>
                  <button v-if="authStore.canDelete" class="action-btn action-btn-del" @click="confirmDelete(d)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 移动端卡片 -->
    <div class="mobile-list">
      <div v-if="paged.length===0" style="text-align:center;padding:48px 0;color:var(--text-muted)">
        {{ loading ? '加载中...' : '暂无设备数据，点击「新增设备」开始添加' }}
      </div>
      <div v-for="d in paged" :key="d.id" class="m-card">
        <div class="m-card-row">
          <div style="display:flex;align-items:center;gap:10px;min-width:0;flex:1">
            <input type="checkbox" :checked="isSelected(d.id)" @change="toggleSelection(d.id)" />
            <div class="m-card-title">{{ d.name }}</div>
          </div>
          <span :class="['tag', validityTag(d.validity)]" style="margin-left:8px;flex-shrink:0">{{ d.validity || '-' }}</span>
        </div>
        <div class="m-card-meta">
          <div class="m-card-meta-item">编号 <b>{{ d.metricNo || '-' }}</b></div>
          <div class="m-card-meta-item">部门 <b>{{ d.dept || '-' }}</b></div>
          <div class="m-card-meta-item">责任人 <b>{{ d.responsiblePerson || '-' }}</b></div>
          <div v-if="d.location" class="m-card-meta-item">位置 <b>{{ d.location }}</b></div>
          <div class="m-card-meta-item">上次校准 <b>{{ d.calDate || '-' }}</b></div>
          <div class="m-card-meta-item">下次校准
            <b :style="{ color: d.validity==='失效' ? 'var(--danger)' : d.validity==='即将过期'?'var(--warning)':'inherit' }">{{ d.nextDate || '-' }}</b>
          </div>
        </div>
        <div class="m-card-footer">
          <div style="display:flex;align-items:center;gap:8px">
            <span :class="['tag', useStatusTag(d.useStatus)]">{{ d.useStatus || '正常' }}</span>
            <a v-if="d.certPath" :href="d.certPath" :download="d.certName||'cert'" class="table-link" style="font-size:13px">📥 证书</a>
            <img v-if="d.imagePath" :src="d.imagePath" style="width:32px;height:32px;object-fit:cover;border-radius:6px;border:1px solid var(--border);cursor:pointer" @click="openImg(d.imagePath)" />
          </div>
          <div class="m-card-actions">
            <button class="action-btn action-btn-view" @click="openPreview(d)">预览</button>
            <button v-if="authStore.canUpdate" class="action-btn action-btn-edit" @click="openModal(d)">编辑</button>
            <button v-if="authStore.canDelete" class="action-btn action-btn-del" @click="confirmDelete(d)">删除</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalElements > 0" style="display:flex;justify-content:flex-end;margin-top:14px">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="() => { currentPage = 1; loadDevices() }"
        @current-change="loadDevices"
      />
    </div>

    <!-- 设备表单弹窗 -->
    <div v-if="showModal" class="modal-mask" @click.self="closeModal">
      <div class="modal-box modal-lg">
        <div class="modal-header">
          <div class="modal-title">{{ editingId ? '✏️ 编辑设备' : '➕ 新增设备' }}</div>
          <button class="modal-close" @click="closeModal">✕</button>
        </div>
        <form @submit.prevent="saveDevice">
          <div class="modal-body" style="padding-top:0">
            <el-tabs v-model="formTab" class="device-form-tabs">
              <!-- Tab 1: 基本信息 -->
              <el-tab-pane label="📋 基本信息" name="basic">
                <el-form label-width="110px" label-position="right" size="default" style="margin-top:12px">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="仪器名称" required>
                        <el-input v-model="form.name" placeholder="请输入仪器名称" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="计量编号" required>
                        <el-input v-model="form.metricNo" placeholder="如：M2024001" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="资产编号">
                        <el-input v-model="form.assetNo" placeholder="资产编号" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="出厂编号">
                        <el-input v-model="form.serialNo" placeholder="出厂编号/序列号" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="设备型号">
                        <el-input v-model="form.model" placeholder="设备型号" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="制造厂">
                        <el-input v-model="form.manufacturer" placeholder="制造厂家名称" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="ABC分类">
                        <el-select v-model="form.abcClass" placeholder="请选择" style="width:100%">
                          <el-option value="" label="未分类" />
                          <el-option value="A类" label="A类" />
                          <el-option value="B类" label="B类" />
                          <el-option value="C类" label="C类" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="使用部门">
                        <el-select v-model="form.dept" placeholder="选择部门" clearable filterable allow-create style="width:100%">
                          <el-option v-for="d in DEPTS" :key="d" :value="d" :label="d" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="设备位置">
                        <el-input v-model="form.location" placeholder="如：一车间" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="使用责任人">
                        <el-input v-model="form.responsiblePerson" placeholder="负责人姓名" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="使用状态">
                        <el-select v-model="form.useStatus" placeholder="选择状态" style="width:100%">
                          <el-option v-for="s in deviceStatuses" :key="s.id" :value="s.name" :label="s.name" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </el-tab-pane>

              <!-- Tab 2: 采购&技术 -->
              <el-tab-pane label="💰 采购&技术" name="purchase">
                <el-form label-width="130px" label-position="right" size="default" style="margin-top:12px">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="采购时间">
                        <el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="选择采购日期" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="采购价格（元）">
                        <el-input-number v-model="form.purchasePrice" :min="0" :precision="2" :step="100" placeholder="0.00" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12" v-if="form.purchaseDate">
                      <el-form-item label="使用年限（自动）">
                        <el-input :value="calcServiceLife" readonly class="input-readonly" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <el-divider content-position="left" style="margin:8px 0 16px">技术参数</el-divider>
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="分度值">
                        <el-input v-model="form.graduationValue" placeholder="如：0.01mm" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="测试范围">
                        <el-input v-model="form.testRange" placeholder="如：0-200mm" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="仪器允许误差">
                        <el-input v-model="form.allowableError" placeholder="如：±0.02mm" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </el-tab-pane>

              <!-- Tab 3: 校准信息 -->
              <el-tab-pane label="📅 校准信息" name="calib">
                <el-form label-width="130px" label-position="right" size="default" style="margin-top:12px">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="检定周期（月）" required>
                        <el-input-number v-model="form.cycle" :min="1" :max="120" placeholder="12" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="校准时间">
                        <el-date-picker v-model="form.calDate" type="date" value-format="YYYY-MM-DD" placeholder="选择校准日期" style="width:100%" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="下次校准（自动）">
                        <el-input :value="calcNextDate || '—'" readonly class="input-readonly" />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="有效性（自动）">
                        <el-input
                          :value="calcValidity"
                          readonly
                          :class="['input-readonly', calcValidity==='失效'?'text-danger':calcValidity==='即将过期'?'text-warning':'']"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="校准结果判定">
                        <el-select v-model="form.calibrationResult" placeholder="请选择" style="width:100%">
                          <el-option value="" label="未判定" />
                          <el-option value="合格" label="合格" />
                          <el-option value="不合格" label="不合格" />
                          <el-option value="降级使用" label="降级使用" />
                          <el-option value="停用" label="停用" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </el-tab-pane>

              <!-- Tab 4: 附件&备注 -->
              <el-tab-pane label="📎 附件&备注" name="attachment">
                <el-form label-width="110px" label-position="right" size="default" style="margin-top:12px">
                  <el-row :gutter="20">
                    <el-col :span="12">
                      <el-form-item label="设备图片">
                        <div class="upload-area">
                          <label class="upload-label">
                            <span>📁 选择图片</span>
                            <input type="file" style="display:none" accept="image/*" @change="handleImageSelect" />
                          </label>
                          <div class="preview-area">
                            <img v-if="imagePreview" :src="imagePreview" class="preview-img" />
                            <span v-if="imagePreview" class="file-chip" @click="removeImage" style="cursor:pointer;color:var(--danger)">✕ 移除</span>
                          </div>
                        </div>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="校准证书">
                        <div class="upload-area">
                          <label class="upload-label">
                            <span>📄 选择证书</span>
                            <input type="file" style="display:none" accept=".pdf,image/*" @change="handleCertSelect" />
                          </label>
                          <div class="preview-area">
                            <div v-if="certInfo" class="file-chip">
                              📄 {{ certInfo }}
                              <span class="remove" @click="removeCert">✕</span>
                            </div>
                          </div>
                        </div>
                      </el-form-item>
                    </el-col>
                    <el-col :span="24">
                      <el-form-item label="备注">
                        <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="备注信息" />
                      </el-form-item>
                    </el-col>
                  </el-row>
                </el-form>
              </el-tab-pane>
            </el-tabs>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline" @click="closeModal">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="saving">
              {{ saving ? '保存中...' : '保存设备' }}
            </button>
          </div>
        </form>
      </div>
    </div>
    <!-- 设备预览弹窗 -->
    <div v-if="showPreview" class="modal-mask" @click.self="showPreview=false">
      <div class="modal-box">
        <div class="modal-header">
          <div class="modal-title">🔍 设备详情 — {{ previewDevice.name }}</div>
          <button class="modal-close" @click="showPreview=false">✕</button>
        </div>
        <div class="modal-body">
          <div class="section-heading">📋 基本信息</div>
          <div class="preview-grid">
            <div class="preview-item"><span class="preview-label">仪器名称</span><span class="preview-val">{{ previewDevice.name }}</span></div>
            <div class="preview-item"><span class="preview-label">计量编号</span><span class="preview-val"><code style="font-size:12px;background:#f1f5f9;padding:2px 6px;border-radius:4px">{{ previewDevice.metricNo }}</code></span></div>
            <div class="preview-item"><span class="preview-label">资产编号</span><span class="preview-val">{{ previewDevice.assetNo || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">出厂编号</span><span class="preview-val">{{ previewDevice.serialNo || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">ABC分类</span><span class="preview-val">{{ previewDevice.abcClass || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">设备型号</span><span class="preview-val">{{ previewDevice.model || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">制造厂</span><span class="preview-val">{{ previewDevice.manufacturer || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">使用部门</span><span class="preview-val">{{ previewDevice.dept || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">设备位置</span><span class="preview-val">{{ previewDevice.location || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">使用责任人</span><span class="preview-val">{{ previewDevice.responsiblePerson || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">使用状态</span><span class="preview-val"><span :class="['tag', useStatusTag(previewDevice.useStatus)]">{{ previewDevice.useStatus || '正常' }}</span></span></div>
          </div>
          <div class="section-sep section-heading">💰 采购信息</div>
          <div class="preview-grid">
            <div class="preview-item"><span class="preview-label">采购时间</span><span class="preview-val">{{ previewDevice.purchaseDate || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">采购价格</span><span class="preview-val">{{ previewDevice.purchasePrice != null ? '¥' + previewDevice.purchasePrice : '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">使用年限</span><span class="preview-val">{{ previewDevice.serviceLife != null ? previewDevice.serviceLife + ' 年' : '-' }}</span></div>
          </div>
          <div class="section-sep section-heading">🔬 技术参数</div>
          <div class="preview-grid">
            <div class="preview-item"><span class="preview-label">分度值</span><span class="preview-val">{{ previewDevice.graduationValue || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">测试范围</span><span class="preview-val">{{ previewDevice.testRange || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">允许误差</span><span class="preview-val">{{ previewDevice.allowableError || '-' }}</span></div>
          </div>
          <div class="section-sep section-heading">📅 校准信息</div>
          <div class="preview-grid">
            <div class="preview-item"><span class="preview-label">检定周期</span><span class="preview-val">{{ previewDevice.cycle ? previewDevice.cycle + ' 月' : '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">上次校准</span><span class="preview-val">{{ previewDevice.calDate || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">下次校准</span><span class="preview-val" :style="{ color: previewDevice.validity==='失效'?'var(--danger)':previewDevice.validity==='即将过期'?'var(--warning)':'inherit', fontWeight: '600' }">{{ previewDevice.nextDate || '-' }}</span></div>
            <div class="preview-item"><span class="preview-label">有效性</span><span class="preview-val"><span :class="['tag', validityTag(previewDevice.validity)]">{{ previewDevice.validity || '-' }}</span></span></div>
            <div class="preview-item"><span class="preview-label">校准结果</span><span class="preview-val">{{ previewDevice.calibrationResult || '-' }}</span></div>
          </div>
          <div v-if="previewDevice.remark" class="section-sep">
            <div class="section-heading">📝 备注</div>
            <div style="padding:10px;background:#f8fafc;border-radius:8px;font-size:13px;color:#475569">{{ previewDevice.remark }}</div>
          </div>
          <div v-if="previewDevice.imagePath || previewDevice.certPath" class="section-sep section-heading">📎 附件</div>
          <div v-if="previewDevice.imagePath || previewDevice.certPath" style="display:flex;gap:16px;align-items:center;margin-top:10px">
            <img v-if="previewDevice.imagePath" :src="previewDevice.imagePath" style="width:100px;height:100px;object-fit:cover;border-radius:10px;border:1px solid var(--border);cursor:pointer" @click="openImg(previewDevice.imagePath)" />
            <a v-if="previewDevice.certPath" :href="previewDevice.certPath" :download="previewDevice.certName||'cert'" class="btn btn-outline btn-sm">📥 下载证书</a>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showPreview=false">关闭</button>
          <button v-if="authStore.canUpdate" class="btn btn-primary" @click="showPreview=false; openModal(previewDevice)">编辑</button>
        </div>
      </div>
    </div>

    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteConfirm" class="modal-mask" @click.self="showDeleteConfirm=false">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title" style="color:var(--danger)">⚠ 确认删除设备</div>
          <button class="modal-close" @click="showDeleteConfirm=false">✕</button>
        </div>
        <div class="modal-body">
          <div style="background:#fff5f5;border:1px solid #fecaca;border-radius:10px;padding:14px;margin-bottom:16px">
            <div style="font-weight:700;font-size:15px;margin-bottom:8px">{{ deleteTarget.name }}</div>
            <div class="preview-grid" style="gap:8px">
              <div class="preview-item"><span class="preview-label">计量编号</span><span class="preview-val">{{ deleteTarget.metricNo }}</span></div>
              <div class="preview-item"><span class="preview-label">使用部门</span><span class="preview-val">{{ deleteTarget.dept || '-' }}</span></div>
              <div class="preview-item"><span class="preview-label">使用责任人</span><span class="preview-val">{{ deleteTarget.responsiblePerson || '-' }}</span></div>
              <div class="preview-item"><span class="preview-label">有效性</span><span class="preview-val"><span :class="['tag', validityTag(deleteTarget.validity)]">{{ deleteTarget.validity || '-' }}</span></span></div>
            </div>
          </div>
          <div style="color:var(--text-muted);font-size:13px">此操作不可撤销，确定要删除该设备吗？</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showDeleteConfirm=false">取消</button>
          <button class="btn btn-danger" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>

    <!-- 批量编辑弹窗 -->
    <div v-if="showBatchEdit" class="modal-mask" @click.self="showBatchEdit=false">
      <div class="modal-box modal-sm">
        <div class="modal-header">
          <div class="modal-title">✏️ 批量修改 — {{ batchEditLabels[batchEditField] }} ({{ selectedIds.length }}台)</div>
          <button class="modal-close" @click="showBatchEdit=false">✕</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">{{ batchEditLabels[batchEditField] }}</label>
            <input v-if="batchEditField==='responsiblePerson' || batchEditField==='location'" v-model="batchEditValue" :placeholder="'请输入' + batchEditLabels[batchEditField]" style="width:100%" />
            <input v-else-if="batchEditField==='dept'" v-model="batchEditValue" list="batch-dept-list" placeholder="选择或输入部门" style="width:100%" />
            <datalist id="batch-dept-list">
              <option v-for="d in DEPTS" :key="d" :value="d" />
            </datalist>
          </div>
          <div style="margin-top:12px;font-size:13px;color:var(--text-muted)">将更新已选 {{ selectedIds.length }} 台设备的「{{ batchEditLabels[batchEditField] }}」字段。</div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-outline" @click="showBatchEdit=false">取消</button>
          <button class="btn btn-primary" @click="saveBatchEdit" :disabled="batchEditSaving">{{ batchEditSaving ? '保存中...' : '批量保存' }}</button>
        </div>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, inject } from 'vue'
import { deviceApi, settingsApi, deviceStatusApi, deptApi } from '../api/index.js'
import { useAuthStore } from '../stores/auth.js'

const showToast = inject('showToast')
const authStore = useAuthStore()
const loading = ref(false)
const devices = ref([])
const deviceStatuses = ref([])
const search = ref(''), filterAssetNo = ref(''), filterSerialNo = ref('')
const filterDept = ref(''), filterValidity = ref(''), filterUseStatus = ref('')
const showModal = ref(false), editingId = ref(null), saving = ref(false)
const formTab = ref('basic')
const importRef = ref(null)
const imagePreview = ref(''), certInfo = ref('')
let pendingImage = null, pendingCert = null
const warningDays = ref(315), expiredDays = ref(360)
const currentPage = ref(1), pageSize = ref(20), totalPages = ref(1), totalElements = ref(0)
const selectedIds = ref([])

// 预览弹窗
const showPreview = ref(false)
const previewDevice = ref({})

// 删除确认弹窗
const showDeleteConfirm = ref(false)
const deleteTarget = ref({})

// 批量编辑弹窗
const showBatchEdit = ref(false)
const batchEditField = ref('')
const batchEditValue = ref('')
const batchEditSaving = ref(false)
const batchEditLabels = { dept: '使用部门', responsiblePerson: '使用责任人', location: '设备位置' }

const DEPTS = ref([])

const form = reactive({
  name:'', metricNo:'', assetNo:'', serialNo:'', abcClass:'', dept:'', location:'',
  manufacturer:'', model:'', responsiblePerson:'', useStatus:'正常', cycle:12,
  calDate:'', calibrationResult:'', purchaseDate:'', purchasePrice:null,
  graduationValue:'', testRange:'', allowableError:'', remark:'',
  imagePath:null, imageName:null, certPath:null, certName:null
})

const calcNextDate = computed(() => {
  if (!form.calDate || !form.cycle) return ''
  const d = new Date(form.calDate)
  d.setMonth(d.getMonth() + parseInt(form.cycle))
  return d.toISOString().split('T')[0]
})
const calcValidity = computed(() => {
  if (!form.calDate) return ''
  const days = Math.floor((new Date() - new Date(form.calDate)) / 86400000)
  if (days < 0) return '有效'
  if (days >= expiredDays.value) return '失效'
  if (days >= warningDays.value) return '即将过期'
  return '有效'
})
const calcServiceLife = computed(() => {
  if (!form.purchaseDate) return ''
  const years = Math.floor((new Date() - new Date(form.purchaseDate)) / (365.25 * 86400000))
  return years + ' 年'
})

const paged = computed(() => devices.value)
const currentPageIds = computed(() => paged.value.map(d => d.id))
const allCurrentPageSelected = computed(() =>
  currentPageIds.value.length > 0 && currentPageIds.value.every(id => selectedIds.value.includes(id))
)

function onFilter() { currentPage.value = 1; clearSelection(); loadDevices() }
function resetFilter() {
  search.value=''; filterAssetNo.value=''; filterSerialNo.value=''
  filterDept.value=''; filterValidity.value=''; filterUseStatus.value=''
  currentPage.value=1; clearSelection(); loadDevices()
}

function validityTag(v) { return v==='有效'?'tag-valid':v==='即将过期'?'tag-warning':'tag-expired' }
function useStatusTag(s) { const map={'正常':'tag-green','故障':'tag-red','维修':'tag-yellow','报废':'tag-gray'}; return map[s]||'tag-blue' }
function openImg(url) { window.open(url) }
function isSelected(id) { return selectedIds.value.includes(id) }
function toggleSelection(id) {
  selectedIds.value = isSelected(id)
    ? selectedIds.value.filter(item => item !== id)
    : [...selectedIds.value, id]
}
function clearSelection() { selectedIds.value = [] }
function toggleSelectCurrentPage() {
  if (allCurrentPageSelected.value) {
    selectedIds.value = selectedIds.value.filter(id => !currentPageIds.value.includes(id))
    return
  }
  selectedIds.value = Array.from(new Set([...selectedIds.value, ...currentPageIds.value]))
}

async function loadDevices() {
  loading.value = true
  try {
    const res = await deviceApi.listPaged({
      search: search.value||undefined,
      assetNo: filterAssetNo.value||undefined,
      serialNo: filterSerialNo.value||undefined,
      dept: filterDept.value||undefined,
      validity: filterValidity.value||undefined,
      useStatus: filterUseStatus.value||undefined,
      page: currentPage.value, size: pageSize.value
    })
    devices.value = res.data.content
    totalPages.value = res.data.totalPages
    totalElements.value = res.data.totalElements
    currentPage.value = res.data.page
    selectedIds.value = selectedIds.value.filter(id => devices.value.some(d => d.id === id))
  } catch(e) { console.error(e) }
  finally { loading.value = false }
}
async function loadStatuses() {
  try { deviceStatuses.value = (await deviceStatusApi.list()).data } catch(e) {}
}

function openModal(device=null) {
  pendingImage=null; pendingCert=null; imagePreview.value=''; certInfo.value=''
  formTab.value = 'basic'
  editingId.value = null
  Object.assign(form, {
    name:'', metricNo:'', assetNo:'', serialNo:'', abcClass:'', dept:'', location:'',
    manufacturer:'', model:'', responsiblePerson:'', useStatus:'正常', cycle:12,
    calDate:'', calibrationResult:'', purchaseDate:'', purchasePrice:null,
    graduationValue:'', testRange:'', allowableError:'', remark:'',
    imagePath:null, imageName:null, certPath:null, certName:null
  })
  if (device) {
    editingId.value = device.id
    Object.assign(form, {
      name:device.name||'', metricNo:device.metricNo||'', assetNo:device.assetNo||'',
      serialNo:device.serialNo||'', abcClass:device.abcClass||'', dept:device.dept||'',
      location:device.location||'', manufacturer:device.manufacturer||'', model:device.model||'',
      responsiblePerson:device.responsiblePerson||'', useStatus:device.useStatus||'正常',
      cycle:device.cycle||12, calDate:device.calDate||'', calibrationResult:device.calibrationResult||'',
      purchaseDate:device.purchaseDate||'', purchasePrice:device.purchasePrice||null,
      graduationValue:device.graduationValue||'', testRange:device.testRange||'',
      allowableError:device.allowableError||'', remark:device.remark||'',
      imagePath:device.imagePath||null, imageName:device.imageName||null,
      certPath:device.certPath||null, certName:device.certName||null
    })
    if (device.imagePath) imagePreview.value = device.imagePath
    if (device.certName) certInfo.value = device.certName
  }
  showModal.value = true
}
function closeModal() { showModal.value = false }

function handleImageSelect(e) {
  const f = e.target.files[0]; if (!f) return
  pendingImage = f
  const reader = new FileReader(); reader.onload = ev => { imagePreview.value = ev.target.result }; reader.readAsDataURL(f)
}
function removeImage() { imagePreview.value=''; pendingImage=null; form.imagePath=null; form.imageName=null }
function handleCertSelect(e) { const f = e.target.files[0]; if (!f) return; pendingCert=f; certInfo.value=f.name }
function removeCert() { certInfo.value=''; pendingCert=null; form.certPath=null; form.certName=null }

async function saveDevice() {
  saving.value = true
  try {
    if (pendingImage) { const r = await deviceApi.uploadFile(pendingImage); form.imagePath=r.data.path; form.imageName=r.data.name }
    if (pendingCert)  { const r = await deviceApi.uploadFile(pendingCert);  form.certPath=r.data.path;  form.certName=r.data.name  }
    const payload = { ...form }
    const res = editingId.value ? await deviceApi.update(editingId.value, payload) : await deviceApi.create(payload)
    closeModal()
    if (res.status === 202) {
      showToast(res.data?.message || '申请已提交，等待管理员审核', 'info')
    } else {
      showToast('保存成功')
    }
    loadDevices()
  } catch(e) { showToast(e.response?.data?.message||'保存失败','error') }
  finally { saving.value=false }
}
function openPreview(d) { previewDevice.value = d; showPreview.value = true }
function confirmDelete(d) { deleteTarget.value = d; showDeleteConfirm.value = true }
async function doDelete() {
  try {
    const res = await deviceApi.remove(deleteTarget.value.id)
    showDeleteConfirm.value = false
    if (res.status === 202) {
      showToast(res.data?.message || '删除申请已提交，等待管理员审核', 'info')
    } else {
      showToast('已删除')
    }
    loadDevices()
  } catch(e) { showToast(e.response?.data?.message||'删除失败','error') }
}
async function deleteDevice(id) {
  if (!confirm('确定要删除该设备吗？')) return
  try { await deviceApi.remove(id); showToast('已删除'); loadDevices() }
  catch(e) { showToast(e.response?.data?.message||'删除失败','error') }
}
function openBatchEdit(field) {
  batchEditField.value = field
  batchEditValue.value = ''
  showBatchEdit.value = true
}
async function saveBatchEdit() {
  if (!batchEditValue.value.trim()) { showToast('请输入内容', 'error'); return }
  batchEditSaving.value = true
  try {
    const payload = { [batchEditField.value]: batchEditValue.value.trim() }
    await Promise.all(selectedIds.value.map(id => deviceApi.update(id, payload)))
    showToast(`已批量更新 ${selectedIds.value.length} 台设备的「${batchEditLabels[batchEditField.value]}」`)
    showBatchEdit.value = false
    loadDevices()
  } catch(e) { showToast('批量更新失败', 'error') }
  finally { batchEditSaving.value = false }
}
async function deleteSelected() {
  if (!selectedIds.value.length) return
  if (!confirm(`确定删除已选中的 ${selectedIds.value.length} 台设备吗？`)) return
  try {
    await Promise.all(selectedIds.value.map(id => deviceApi.remove(id)))
    showToast(`已删除 ${selectedIds.value.length} 台设备`)
    clearSelection()
    loadDevices()
  } catch(e) {
    showToast(e.response?.data?.message || '批量删除失败', 'error')
  }
}
function triggerImport() { importRef.value.click() }
async function handleImport(e) {
  const f = e.target.files[0]; if (!f) return
  try { const r = await deviceApi.import(f); showToast(r.data.message||'导入成功'); loadDevices() }
  catch(e) { showToast('导入失败：'+(e.response?.data?.message||e.message),'error') }
  finally { e.target.value='' }
}
async function exportFiltered() {
  try {
    const r = await deviceApi.export({
      search: search.value||undefined,
      assetNo: filterAssetNo.value||undefined,
      serialNo: filterSerialNo.value||undefined,
      dept: filterDept.value||undefined,
      validity: filterValidity.value||undefined,
      useStatus: filterUseStatus.value||undefined
    })
    const url = URL.createObjectURL(r.data)
    Object.assign(document.createElement('a'), { href:url, download:'设备台账.xlsx' }).click()
    URL.revokeObjectURL(url)
  } catch(e) { showToast('导出失败','error') }
}
async function exportAll() {
  try {
    const r = await deviceApi.export({})
    const url = URL.createObjectURL(r.data)
    Object.assign(document.createElement('a'), { href:url, download:'设备台账-全部.xlsx' }).click()
    URL.revokeObjectURL(url)
  } catch(e) { showToast('导出失败','error') }
}
async function downloadTemplate() {
  try {
    const r = await deviceApi.template()
    const url = URL.createObjectURL(r.data)
    Object.assign(document.createElement('a'), { href:url, download:'导入模板.xlsx' }).click()
    URL.revokeObjectURL(url)
  } catch(e) { showToast('下载失败','error') }
}

onMounted(async () => {
  loadDevices(); loadStatuses()
  try { const r = await settingsApi.get(); warningDays.value=r.data.warningDays||315; expiredDays.value=r.data.expiredDays||360 } catch(e) {}
  try { const r = await deptApi.list(); DEPTS.value = r.data.map(d => d.name) } catch(e) {}
})
</script>

<style scoped>
.text-danger { color: var(--danger) !important; }
.text-warning { color: var(--warning) !important; }
.preview-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px 20px; margin-top: 8px;
}
.preview-item { display: flex; flex-direction: column; gap: 2px; }
.preview-label { font-size: 11.5px; color: var(--text-muted); }
.preview-val { font-size: 13.5px; color: var(--text); }
.batch-bar {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:12px;
  padding:10px 14px;
  margin-bottom:12px;
  background:#fff;
  border:1px solid var(--border);
  border-radius:12px;
}
.batch-info { color:var(--text-muted); font-size:13px; }
.batch-actions { display:flex; gap:8px; flex-wrap:wrap; }
.device-form-tabs :deep(.el-tabs__header) { padding: 0 24px; margin-bottom: 0; }
.device-form-tabs :deep(.el-tabs__content) { padding: 0 24px 8px; }
</style>
