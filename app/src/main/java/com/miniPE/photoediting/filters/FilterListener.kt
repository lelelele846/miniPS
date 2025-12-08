package com.miniPE.photoediting.filters

import ja.miniPE.photoeditor.PhotoFilter

interface FilterListener {
    fun onFilterSelected(photoFilter: PhotoFilter)
}